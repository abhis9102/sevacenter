-- Counter bookings (ADR 0026): staff book a puja or issue a darshan pass for a walk-in devotee.
-- A walk-in may give no phone or email, so "every booking has a contact" becomes "a contact, or
-- the staff member who made it". A paid counter booking records how the dakshina was taken.

ALTER TABLE puja_booking
    ADD COLUMN booked_by         BIGINT REFERENCES app_user(id),
    ADD COLUMN counter_mode      TEXT CHECK (counter_mode IN ('CASH', 'UPI', 'CARD', 'CHEQUE', 'BANK_TRANSFER')),
    ADD COLUMN counter_reference TEXT CHECK (length(counter_reference) <= 64),
    DROP CONSTRAINT puja_booking_contact,
    ADD CONSTRAINT puja_booking_contact CHECK (phone IS NOT NULL OR email IS NOT NULL OR booked_by IS NOT NULL),
    -- Only staff record a counter payment, and a paid counter booking always says how it was paid.
    ADD CONSTRAINT puja_booking_counter_shape CHECK (
        (booked_by IS NULL AND counter_mode IS NULL AND counter_reference IS NULL)
        OR (booked_by IS NOT NULL AND (amount_paise = 0) = (counter_mode IS NULL)));

ALTER TABLE event_pass
    ADD COLUMN issued_by BIGINT REFERENCES app_user(id),
    DROP CONSTRAINT event_pass_contact,
    ADD CONSTRAINT event_pass_contact CHECK (phone IS NOT NULL OR email IS NOT NULL OR issued_by IS NOT NULL);
