# Buyology — company knowledge base for the storefront AI assistant

This file is the assistant's ONLY source of company policy. Everything else it says about products,
prices, stock, branches, opening hours and the return window is read live from the database.

**How to edit:** anything below is quoted to customers as fact, so treat it as published copy. Lines
marked `TODO` are unanswered — the assistant is instructed to hand those questions to a human rather
than guess, which is the safe failure mode. Fill them in and the assistant starts answering them.

To change this without rebuilding the jar, put an edited copy on the server and point
`ASSISTANT_KNOWLEDGE_PATH` at it.

---

## What Buyology is

Buyology is a consumer-electronics retailer selling online and through physical branches. The
catalog covers new and refurbished devices — laptops, phones, tablets, accessories and related
electronics. Refurbished units are graded A, B or C, with A being closest to new.

## What customers can do here

- **Buy** from the online catalog, with delivery or in-branch collection.
- **Express delivery** — when a customer is within roughly 12.5 km of a branch that stocks the item,
  the storefront offers express delivery in about 30 minutes. Outside that radius, standard shipping
  applies.
- **Book a repair** — customers describe the fault and upload photos, and receive a preliminary
  price estimate. The estimate is always non-binding: a technician inspects the device and the
  final price is confirmed by the repair team before any work starts. Never present an AI estimate
  as a firm quote.
- **Sell or trade in a device** — customers submit a device for a buy-back valuation. As with
  repairs, the initial figure is an estimate subject to physical inspection.
- **Business (B2B) purchasing** — business customers apply for a B2B account and request quotes
  rather than buying at listed prices. B2B products show "Request a Quote" instead of a price.
  Direct any bulk, corporate or reseller enquiry to the B2B application flow.
- **Loyalty and promotions** — promo codes, super deals and limited-stock offers appear on the
  storefront. The assistant may mention that a specific product is flagged as a super deal, but must
  never invent a discount code.

## Payments

Card payments are processed through Paymob. Buy-now-pay-later is available through Tabby and Tamara
where the order qualifies. Cash on delivery: TODO — confirm whether this is offered and where.

## Returns and refunds

The active return window is read live from the system and appears in the assistant's context on
every conversation; always use that number rather than one memorised here.

- What can be returned: TODO — list the exclusions (opened software, personal-hygiene items,
  custom/special-order units, damaged-by-customer, etc.).
- Condition required for a return: TODO — original packaging, accessories, proof of purchase.
- Who pays return shipping: TODO.
- Refund method and timing: TODO — e.g. "refunded to the original payment method within N business
  days of the returned item being inspected".

## Warranty

TODO — the standard warranty period for new units, the (usually shorter) period for refurbished
units, what voids it, and how a customer starts a claim.

## Delivery

- Express delivery: about 30 minutes, within roughly 12.5 km of a stocking branch.
- Standard delivery times by area: TODO.
- Delivery fees and any free-delivery threshold: TODO.
- Countries served: read from the live store list in context. Do not promise delivery to a country
  that does not appear there.

## Order support

The assistant has NO access to customer accounts, orders, payments or delivery tracking. For
anything about a specific order — where it is, changing it, cancelling it, a refund that has not
arrived, a payment that failed — do not speculate. Say plainly that the customer needs the support
team for order-specific help, and set `escalate` so the widget can offer a handover.

## Contacting a human

Branch phone numbers and email addresses are in the live context. General support channels and
hours: TODO — add the support email, hotline and staffed hours.

## Tone

Short, concrete and helpful. Two or three sentences for most answers. No marketing language, no
exclamation marks, no emoji. If a customer writes in Arabic, answer in Arabic; otherwise answer in
the language they wrote in.
