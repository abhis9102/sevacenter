-- A devotee books a puja online and pays the dakshina at the temple (cash, UPI or card at the
-- counter). The booking waits as PAY_AT_TEMPLE until staff collect it; collected_by records who.
-- The old rule still holds: a paid booking is confirmed only with a payment record.

ALTER TABLE puja_booking DROP CONSTRAINT puja_booking_status_check;
ALTER TABLE puja_booking ADD CONSTRAINT puja_booking_status_check
    CHECK (status IN ('AWAITING_PAYMENT', 'PAY_AT_TEMPLE', 'CONFIRMED', 'PERFORMED', 'CANCELLED'));

ALTER TABLE puja_booking DROP CONSTRAINT puja_booking_paid;
ALTER TABLE puja_booking ADD CONSTRAINT puja_booking_paid CHECK (
    status IN ('AWAITING_PAYMENT', 'PAY_AT_TEMPLE', 'CANCELLED')
    OR (amount_paise = 0 AND payment_ref IS NULL)
    OR (amount_paise > 0 AND payment_ref IS NOT NULL));
-- Only a paid puja waits for payment at the temple.
ALTER TABLE puja_booking ADD CONSTRAINT puja_booking_pay_at_temple CHECK (status <> 'PAY_AT_TEMPLE' OR amount_paise > 0);

ALTER TABLE puja_booking ADD COLUMN collected_by BIGINT REFERENCES app_user(id);
ALTER TABLE puja_booking DROP CONSTRAINT puja_booking_counter_shape;
ALTER TABLE puja_booking ADD CONSTRAINT puja_booking_counter_shape CHECK (
    (booked_by IS NULL AND collected_by IS NULL AND counter_mode IS NULL AND counter_reference IS NULL)
    OR (booked_by IS NOT NULL AND collected_by IS NULL AND (amount_paise = 0) = (counter_mode IS NULL))
    OR (booked_by IS NULL AND collected_by IS NOT NULL AND amount_paise > 0 AND counter_mode IS NOT NULL));
