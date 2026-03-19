package com.buyology.ecommerce.review.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Validates review/question body text against:
 *  - SQL injection structural patterns
 *  - XSS / HTML injection patterns
 *  - Common profanity in English, Arabic, and Azerbaijani
 *
 * Returns null when the text is clean, or a user-facing error message when blocked.
 */
@Service
public class ReviewContentFilter {

    // SQL injection — structural patterns only (not bare keywords that appear in normal prose)
    private static final Pattern SQL_INJECTION = Pattern.compile(
            "(?i)(" +
            "'\\s*(OR|AND)\\s+'|" +               // ' OR ' / ' AND '
            "\\bUNION\\s+(ALL\\s+)?SELECT\\b|" +  // UNION SELECT
            "';\\s*DROP\\b|" +                     // '; DROP
            "--[\\s;]|--$|" +                      // SQL comment markers
            "/\\*.*?\\*/|" +                       // /* block comments */
            "\\bEXEC(UTE)?\\s*\\(|" +              // EXEC( / EXECUTE(
            "\\bDECLARE\\s+@|" +                   // DECLARE @var (T-SQL)
            "\\bCAST\\s*\\(.*?\\bCHAR\\b|" +       // CAST(... CHAR
            "\\bCONVERT\\s*\\(.*?\\bCHAR\\b|" +    // CONVERT(... CHAR
            "0x[0-9a-fA-F]{4,}|" +                 // hex literals like 0x41414141
            "CHAR\\s*\\(\\s*\\d" +                 // CHAR(65 etc.
            ")",
            Pattern.DOTALL
    );

    // XSS / HTML injection
    private static final Pattern XSS = Pattern.compile(
            "(?i)(" +
            "<\\s*script[\\s>]|</\\s*script|" +                             // <script> tags
            "javascript\\s*:|vbscript\\s*:|" +                              // JS/VBS URIs
            "data\\s*:[^,]{0,20}script|" +                                  // data:…script URIs
            "\\bon\\w{1,20}\\s*=|" +                                        // event handlers: onload= onclick= etc.
            "eval\\s*\\(|setTimeout\\s*\\(|setInterval\\s*\\(|" +           // eval / timers
            "expression\\s*\\(|" +                                          // CSS expression()
            "<\\s*(iframe|object|embed|link|meta|base|form|input|button)[\\s>/]|" + // dangerous tags
            "document\\s*\\.\\s*cookie|window\\s*\\.\\s*location|" +        // cookie / redirect grabs
            "src\\s*=\\s*[\"']?\\s*http" +                                  // external src= injections
            ")",
            Pattern.DOTALL
    );

    // English profanity — \b works fine for ASCII
    private static final Pattern PROFANITY_EN = Pattern.compile(
            "(?i)\\b(" +
            "fuck(ing|er|ed|s)?|" +
            "sh[i1]t(s|ty)?|bullsh[i1]t|" +
            "a[s$]{2}(hole|es?)?|" +
            "b[i1]tch(es|y)?|" +
            "c[u*]nt(s)?|" +
            "d[i1]ck(head|s)?|" +
            "c[o0]ck(s)?|" +
            "p[u*]ssy|p[u*]ss[i1]es|" +
            "n[i1]gg[ae]r?s?|" +
            "fagg?[o0]t(s)?|" +
            "wh[o0]re(s)?|" +
            "bastard(s)?|" +
            "sl[u*]t(s)?" +
            ")\\b"
    );

    // Azerbaijani profanity — uses Unicode-aware boundaries because of ə, ü, ö, ğ, ş, ç
    private static final Pattern PROFANITY_AZ = Pattern.compile(
            "(?i)(?<![\\p{L}\\d])(" +
            "sik(dir|tir|in|ib|iş)?|" +   // penis / fuck / fuck off
            "siktir(in)?|" +               // fuck off (standalone)
            "g[oö]t(ver|ə)?|" +            // ass / give ass (göt + ASCII variant got)
            "am(c[ıi]q)?|" +               // vagina (vulgar)
            "orospu(çu|çuluq|luq)?|" +     // whore / son of whore
            "piç|" +                       // bastard
            "fahiş[əe]|" +                 // prostitute
            "eşş[əe]k|" +                  // donkey (used as insult)
            "itin\\s+oğlu|" +              // son of a dog (phrase)
            "göt\\s*ver" +                 // explicit phrase
            ")(?![\\p{L}\\d])"
    );

    // Arabic profanity — Arabic script is self-delimiting so no word-boundary anchors needed
    private static final Pattern PROFANITY_AR = Pattern.compile(
            // sexual / highly vulgar terms
            "كس|" +                        // vagina (very vulgar)
            "طيز|" +                       // ass / buttocks
            "زب|" +                        // penis
            "نيك|ينيك|تنيك|" +             // fuck (root + conjugations)
            "شرموط[ةه]|شراميط|" +          // whore (f/pl)
            "خول|" +                       // gay (derogatory)
            "منيوك|متناك|" +               // fucked (passive derogatory)
            "كسمك|كس\\s*أمك|" +            // your mother's vagina
            "عاهر[ةه]|عواهر|" +            // prostitute (f/pl)
            "زاني[ةه]|" +                  // adulteress
            "مأبون|" +                     // sodomite (derogatory)
            "ابن\\s*[اإ]لشرموط[ةه]|" +    // son of a whore
            "يلعن\\s*[أا]مك"               // damn your mother
    );

    /**
     * @return null if the text passes all checks, or a user-facing error message if blocked.
     */
    public String check(String text) {
        if (text == null || text.isBlank()) return null;

        if (SQL_INJECTION.matcher(text).find()) {
            return "Review contains invalid characters or patterns";
        }
        if (XSS.matcher(text).find()) {
            return "Review contains invalid content";
        }
        if (PROFANITY_EN.matcher(text).find() ||
            PROFANITY_AZ.matcher(text).find() ||
            PROFANITY_AR.matcher(text).find()) {
            return "Review contains inappropriate language";
        }
        return null;
    }
}
