# Sign in with Apple — deployment requirements

The website uses Apple's popup SDK and a Services ID. The iOS app uses Expo's native Apple Authentication button and its bundle ID. Both authenticate against `/auth/apple/callback`, which verifies Apple's RS256 signature, issuer, audience, expiry, issued-at, subject, and a single-use server nonce. Redis challenges expire after five minutes and are consumed atomically across backend nodes. Apple's public keys are cached for an hour and refreshed on rotation. Existing `/auth/**` rate limiting applies to challenges and callbacks.

## Owner checklist

1. In your existing Apple Developer team, enable **Sign in with Apple** on the production App ID **com.buyology.buyology**. Regenerate signing profiles through the iOS build process.
2. Create/configure a website **Services ID**, associate it with primary App ID `com.buyology.ecommerce`; group production mobile App ID `com.buyology.buyology` under the same primary, and register the production domains and exact HTTPS Return URLs. Using the same primary App ID groups website and mobile identities. The Services ID is not the app bundle ID.
3. Create a Sign in with Apple key for the primary app. Keep the downloaded `.p8` securely; record the **Team ID** and **Key ID**. Do not send the private key in chat or commit it.
4. Register sending domains/email addresses in Apple's private email relay configuration and verify SPF/DKIM as required. Test an order email to a Hide My Email address.
5. Configure the variables below, deploy backend first, rebuild/deploy the website, and build a new signed iOS app. Verify first login, repeat login, cancellation, Hide My Email, email delivery, and login to the same account on website and mobile with real Apple credentials.

## Backend runtime configuration (both nodes)

```
APPLE_TEAM_ID=<Apple team ID>
APPLE_CLIENT_ID=com.buyology.ecommerce.login
APPLE_IOS_CLIENT_ID=com.buyology.buyology
APPLE_KEY_ID=<Sign in with Apple key ID>
APPLE_PRIVATE_KEY=<complete .p8 PEM contents>
APPLE_REDIRECT_URI=https://buyology.online
```

The private key supports actual PEM newlines or literal `\n` separators. Compose forwards the new iOS client ID. Redis must be shared across nodes, with GETDEL support (Redis 6.2+); authentication fails closed if challenges cannot be consumed. Server clock must be accurate. Outbound HTTPS to `appleid.apple.com` must work.

The production mobile bundle ID is defined in `buyology-storemobile/env.ts`. Preview and development use different bundle IDs; production accepts only the configured iOS ID. Test production identity using a signed production/TestFlight build, or use a separate test backend configured for the test bundle ID.

## Website build configuration

```
NEXT_PUBLIC_APPLE_CLIENT_ID=com.buyology.ecommerce.login
NEXT_PUBLIC_APPLE_REDIRECT_URI=https://buyology.online
```

Register the exact Return URL above with Apple. For a v2-domain deployment, use and register `https://v2.buyology.online` instead. The backend exchanges the authorization code using the requesting website's return URL. The popup flow does not require a separate website callback handler. Public variables are embedded at build time, so changing runtime environment alone does not enable the button. The button is hidden without a Services ID to avoid presenting an unconfigured login.

## Mobile build configuration

Native Apple login is enabled by default on supported iOS devices. Set `EXPO_PUBLIC_ENABLE_APPLE_SIGNIN=false` to disable it during rollout. Existing Expo config includes `usesAppleSignIn: true` and `expo-apple-authentication`. Membership alone does not activate the capability for every app; the app's signing profile must include it. Android does not display the native button; a separate browser-based Android Apple login is outside this implementation.

## Account behavior and rollout

Returning customers are found by Apple's verified subject ID and provider. First-consent names are stored on creation; later missing names do not erase them. Email comes from the verified token, including private relay addresses. Suspended accounts cannot sign in. Existing password accounts are not automatically merged by matching email: account linking requires a separately authenticated flow.

Old clients without a server challenge are rejected. Deploy backend before the new clients, keeping old Apple buttons disabled during transition. No credentials, Apple-console changes, live sign-in, or App Store submission are performed by the code change itself.

Official references:
- https://developer.apple.com/help/account/capabilities/configure-sign-in-with-apple-for-the-web/
- https://developer.apple.com/help/account/capabilities/create-a-sign-in-with-apple-private-key
- https://developer.apple.com/help/account/capabilities/configure-private-email-relay-service/
- https://developer.apple.com/documentation/signinwithapple/verifying-a-user

## GitHub Actions credential selection

Preserve existing `APPLE_*` secrets. New credentials are stored as `APPLE_LOGIN_V2_TEAM_ID`, `APPLE_LOGIN_V2_CLIENT_ID`, `APPLE_LOGIN_V2_KEY_ID`, `APPLE_LOGIN_V2_PRIVATE_KEY`, and `APPLE_LOGIN_V2_REDIRECT_URI`. Repository variable `APPLE_LOGIN_USE_V2=true` selects the entire new set; absent/false selects the legacy set. A missing selected V2 credential does not fall back to a legacy credential, avoiding a mixed key pair. `APPLE_IOS_CLIENT_ID` remains separate. Changing the switch affects the next deployment, not running containers. Credential rollback does not reverse Apple-console grouping or client/backend contract changes; coordinate application versions separately.
