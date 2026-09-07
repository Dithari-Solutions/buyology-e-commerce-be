package com.buyology.ecommerce.verification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Everything that must be true before Buyology pays Twilio to send an SMS.
 *
 * <p>Written after an SMS-pumping attack: an attacker with an account looped the send-OTP endpoint
 * with international numbers and took the carrier's revenue share on every message, running the
 * balance to -$383 and triggering three auto-recharges in four days. Nothing stopped it, because
 * nothing here existed — {@code sendPhoneOtp} trimmed the string and handed it straight to Twilio,
 * and the endpoint's path matched none of the rate limiter's credential patterns.
 *
 * <p>Three independent gates, cheapest first:
 * <ol>
 *   <li><b>Shape</b> — a number that is not valid E.164 never reaches Twilio. A malformed number
 *       used to cost a live API round trip to learn what a regex knows for free.</li>
 *   <li><b>Destination</b> — only allow-listed country codes. Pumping is only profitable to
 *       expensive destinations, and Buyology sells in one country. This is the gate that makes the
 *       attack unprofitable rather than merely slower.</li>
 *   <li><b>Quota</b> — per number, per account, and a global daily ceiling. The first two stop the
 *       obvious loops; the global cap is a circuit breaker, so the worst case of a bypass nobody
 *       has thought of yet is one bounded day of spend rather than an open tap.</li>
 * </ol>
 *
 * <p>Unlike {@link com.buyology.ecommerce.auth.service.LoginAttemptService}, which fails open when
 * Redis is unreachable, this fails CLOSED. Failing open there costs a missed lockout; failing open
 * here costs money to an attacker who is already looking for exactly that. If we cannot count, we
 * do not spend.
 */
@Service
public class PhoneVerificationGuard {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationGuard.class);

    /** E.164: a leading +, a non-zero country digit, then 7–14 more. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private static final String NUMBER_PREFIX = "otp:send:number:";
    private static final String ACCOUNT_PREFIX = "otp:send:account:";
    private static final String GLOBAL_PREFIX = "otp:send:global:";

    private static final Duration NUMBER_WINDOW = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofHours(25);

    private final StringRedisTemplate redis;

    /**
     * Country codes we will send to, without the +. Defaults to the UAE alone because that is
     * where Buyology sells; widening it is a property change, not a deploy. Every entry added
     * here is a destination an attacker may target, so add deliberately.
     */
    private final List<String> allowedCountryCodes;

    private final int maxPerNumberPerHour;
    private final int maxPerAccountPerDay;
    private final int maxGlobalPerDay;

    public PhoneVerificationGuard(
            StringRedisTemplate redis,
            @Value("${verification.allowed-country-codes:971}") String allowedCountryCodes,
            @Value("${verification.max-sends-per-number-per-hour:3}") int maxPerNumberPerHour,
            @Value("${verification.max-sends-per-account-per-day:5}") int maxPerAccountPerDay,
            @Value("${verification.max-sends-per-day:500}") int maxGlobalPerDay) {
        this.redis = redis;
        this.allowedCountryCodes = Arrays.stream(allowedCountryCodes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.maxPerNumberPerHour = maxPerNumberPerHour;
        this.maxPerAccountPerDay = maxPerAccountPerDay;
        this.maxGlobalPerDay = maxGlobalPerDay;
    }

    /**
     * Normalises the number and throws unless this send is allowed.
     *
     * @param quotaSubject whoever is accountable: a user id for a signed-in customer, an
     *                     application id for the unauthenticated supplier form. Both must be
     *                     counted — that form needs no account at all.
     * @return the normalised E.164 number to send to, which is what should be persisted
     * @throws IllegalArgumentException if the number is unusable or the destination is not served
     * @throws IllegalStateException    if a quota is exhausted or the counters cannot be read
     */
    public String check(String quotaSubject, String rawPhoneNumber) {
        String phone = normalise(rawPhoneNumber);

        if (!E164.matcher(phone).matches()) {
            // Cheapest possible rejection. The logged attack included "+9718590518973" — thirteen
            // digits behind a country code that takes nine — and every attempt like it was a paid
            // round trip to Twilio to be told what this line settles for nothing.
            throw new IllegalArgumentException(
                    "That phone number does not look right. Enter it in international format, "
                            + "for example +971 50 123 4567.");
        }

        if (allowedCountryCodes.stream().noneMatch(cc -> phone.startsWith("+" + cc))) {
            log.warn("[OTP-GUARD] Blocked send to unserved destination {} for {}",
                    mask(phone), quotaSubject);
            throw new IllegalArgumentException(
                    "We can only send verification codes to UAE numbers at the moment.");
        }

        // Quotas last: they cost a Redis round trip, and the two checks above are free.
        consume(NUMBER_PREFIX + phone, maxPerNumberPerHour, NUMBER_WINDOW,
                "Too many codes requested for this number. Please wait an hour and try again.");
        consume(ACCOUNT_PREFIX + quotaSubject, maxPerAccountPerDay, DAY,
                "You have requested too many verification codes today. Please try again tomorrow.");
        consume(GLOBAL_PREFIX + LocalDate.now(ZoneOffset.UTC), maxGlobalPerDay, DAY,
                "Verification is temporarily unavailable. Please try again later.");

        return phone;
    }

    /**
     * Strips the punctuation people type and accepts the common ways of writing a country code,
     * so a legitimate number is not rejected for cosmetic reasons. It deliberately does NOT guess
     * a missing country code: assuming one is how a nine-digit local number becomes a thirteen-digit
     * international one, and a wrong guess is a message sent to a stranger at our expense.
     */
    private static String normalise(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[\\s()\\-.]", "");
        if (cleaned.startsWith("00")) cleaned = "+" + cleaned.substring(2);
        return cleaned;
    }

    /** Increments a counter, sets its TTL on first use, and throws once the limit is passed. */
    private void consume(String key, int limit, Duration window, String message) {
        Long used;
        try {
            used = redis.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redis.expire(key, window);
            }
        } catch (Exception e) {
            // Fails closed on purpose — see the class comment.
            log.error("[OTP-GUARD] Redis unavailable, refusing to send: {}", e.getMessage());
            throw new IllegalStateException(
                    "Verification is temporarily unavailable. Please try again shortly.");
        }
        if (used != null && used > limit) {
            log.warn("[OTP-GUARD] Quota exhausted for {} ({} of {})", key, used, limit);
            throw new IllegalStateException(message);
        }
    }

    /** Last four digits only — the rest never reaches a log line. */
    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "***" : "***" + phone.substring(phone.length() - 4);
    }
}
