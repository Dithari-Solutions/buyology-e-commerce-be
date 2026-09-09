package com.buyology.ecommerce.customeremail.controller;

import com.buyology.ecommerce.customeremail.service.CustomerEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The unsubscribe link carried by every customer email.
 *
 * <p>Unauthenticated by necessity — it is clicked from an inbox, where nobody is signed in — and it
 * returns a page rather than JSON for the same reason. It mirrors the newsletter's unsubscribe
 * endpoint, including its known weakness: a link in an email can be prefetched by a scanner, which
 * would opt someone out without them clicking. That is a real flaw and it is inherited knowingly;
 * fixing it properly means a confirmation step on the page, which is worth doing but is not this
 * change.
 */
@RestController
@Tag(name = "Email opt-out")
public class EmailOptOutController {

    private final CustomerEmailService service;

    public EmailOptOutController(CustomerEmailService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/email/opt-out", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Stop sending marketing email to this customer")
    public ResponseEntity<String> optOut(@RequestParam("token") String token) {
        boolean ok;
        try {
            ok = service.optOut(UUID.fromString(token));
        } catch (IllegalArgumentException badUuid) {
            ok = false;
        }
        String message = ok
                ? "You have been unsubscribed. You will not receive marketing email from Buyology again."
                : "That unsubscribe link is not valid. Please contact support if you keep receiving email.";
        return ResponseEntity.ok("""
                <!doctype html><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Buyology</title>
                <div style="font-family:system-ui,sans-serif;max-width:34rem;margin:4rem auto;padding:0 1.5rem;
                            line-height:1.6;color:#1a1626">
                  <h1 style="font-size:1.35rem;margin:0 0 .75rem">Buyology</h1>
                  <p>""" + message + """
                  </p>
                  <p style="margin-top:1.5rem">
                    <a href="https://buyology.online" style="color:#402f75">Back to Buyology</a>
                  </p>
                </div>
                """);
    }
}
