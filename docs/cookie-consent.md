# Storefront cookie preferences

The storefront saves an explicit analytics/marketing choice locally and submits an
anonymous decision to this API. Necessary cart, account and payment storage stays
available. This is a history of browser-declared choices, not proof of a customer's
identity and not a count of unique customers.

## API

- `POST /api/privacy/consents` is anonymous and write-only. JSON fields:
  `decisionId` and `consentId` (UUIDs), `analytics` and `marketing` (booleans),
  `policyVersion: "2026-10-16"`, `source: "BANNER" | "SETTINGS"`, and
  `language: "en" | "az" | "ar"`. The standard response envelope's `data` is
  `{ "decisionId": "…", "recorded": true }`.
- Replaying the same decision is idempotent, including concurrent requests to
  different replicas. Reusing an ID with different choices fails validation.
  Changing a preference requires a new decision ID; the consent ID stays stable.
- `GET /api/privacy/cookies` returns the public policy version, choice lifetime,
  audit retention and cookie/storage inventory. It contains no visitor records.
- `GET /api/admin/privacy/consents` requires `ROLE_SUPERADMIN`. Query parameters:
  zero-based `page`, `size` (1–100), optional full UUID `consentId`, and optional
  boolean `analytics` / `marketing`. Newest decisions appear first. All other
  roles are denied by method security, independently of the dashboard UI.

The public write uses the analytics beacon rate-limit tier and a local fallback
when Redis is unavailable; it does not consume the shopping/checkout tier.

## Data and retention

Flyway `V67__cookie_consent_events.sql` creates the consent event table and indexes.
Records contain references, choices, policy, source, language, server receipt time
and an allowlisted website hostname derived from Origin. They never contain raw
cookies, tokens, customer identity, IP addresses, user agents or referrer paths.
Origin is descriptive metadata, not authentication. Public POST responses do not
disclose previously stored decision details.

Choices last 180 days in the browser. Audit retention defaults to 365 days;
`app.privacy.consent-retention-days` can configure it, with a minimum of 180.
The scheduled purge runs daily at 03:35 Asia/Dubai. Coordinate changes to the
published browser policy, inventory and retention before changing these values.

## Deployment and validation

Deploy the backend before the dashboard and storefront. The existing main-branch
CI runs tests, builds a commit-tagged image and rolls both application replicas.
Confirm `/api/privacy/cookies` returns the new policy, then make a choice on the
deployed storefront and check **Dashboard → Cookie consent** as a superadmin.
Older visits are not backfilled. Offline/failed audit submissions retry from the
browser; a local choice remains effective even if its audit request fails.

Local verification: 43 focused consent/security/rate-limit/analytics/HTTP-status tests passed.
PostgreSQL/Testcontainers integration tests could not run locally because the
Docker daemon was unavailable; the existing CI runs them before deployment.

The same release preserves intentional HTTP status exceptions in the common error
handler. A missing public product slug now returns 404 rather than a generic 500.
Unexpected upstream failures retain their error status and hide internal details.
