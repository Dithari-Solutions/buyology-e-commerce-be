# Partnership invitation links

Manual invitations keep the template's direct HTTPS links to
`https://business.buyology.online`. The SendGrid Mail Send request explicitly sets
`tracking_settings.click_tracking.enable` and `enable_text` to `false` so account
click tracking cannot replace the application URL with a branded redirect.
See [SendGrid's tracking settings](https://www.twilio.com/docs/sendgrid/api-reference/mail-send/mail-send#body-tracking_settings).

This fixes new invitation sends, including already-queued invitations sent after
the updated backend is deployed. It does not change the email copy or recipient
queue. Other email types retain their existing tracking configuration.

On 19 October 2026, `url7151.buyology.online` resolved to `sendgrid.net` and failed
normal certificate hostname verification. The direct business website passed TLS
verification and opened its application form with HTTP 200 in Chrome.

Already-delivered messages contain the previous redirect URL and cannot be
rewritten by a backend release. Those recipients can use the direct website or
receive a fresh invitation after deployment. Restoring old tracked links requires
correcting branded-link HTTPS in the SendGrid/DNS configuration; no account/DNS
changes or automatic resends are performed by this fix.

Verification: all 854 unit tests pass. The invitation transport test checks the
exact HTML/CTA and both serialized click-tracking flags. Provider calls are mocked;
no real emails are sent during tests.
