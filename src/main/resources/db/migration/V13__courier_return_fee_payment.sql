-- Courier return-pickup fee, paid separately by the customer via Paymob.
--
-- The fee is a standalone charge (NOT tied to an order) that the customer pays
-- right after requesting a courier pickup for a refund return. It is recorded as
-- a normal payment_transactions row tagged with purpose = 'COURIER_RETURN_FEE'
-- and linked back to the refund request, so it surfaces as delivery-fee revenue
-- without a dedicated table.

ALTER TABLE payment_transactions
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'ORDER',
    ADD COLUMN refund_request_id UUID;

CREATE INDEX idx_payment_transactions_purpose ON payment_transactions(purpose);
CREATE INDEX idx_payment_transactions_refund_request ON payment_transactions(refund_request_id);
