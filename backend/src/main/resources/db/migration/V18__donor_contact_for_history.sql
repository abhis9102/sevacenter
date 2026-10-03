-- MVP: donation history in "my seva" (ADR 0019). An online donor may leave a mobile number or
-- email, so a later verified login with that contact (ADR 0018) can find the donation again.
-- Optional: donating never requires it. Same formats as every other contact column.
ALTER TABLE payment_intent ADD COLUMN donor_phone TEXT CHECK (donor_phone ~ '^\+[1-9][0-9]{7,14}$');
ALTER TABLE payment_intent ADD COLUMN donor_email TEXT CHECK (length(donor_email) <= 254 AND donor_email = lower(donor_email));
CREATE INDEX idx_payment_intent_donor_phone ON payment_intent(tenant_id, donor_phone) WHERE donor_phone IS NOT NULL;
CREATE INDEX idx_payment_intent_donor_email ON payment_intent(tenant_id, donor_email) WHERE donor_email IS NOT NULL;
-- Staff-recorded donations are found through the linked devotee's phone/email.
CREATE INDEX idx_devotee_phone ON devotee(tenant_id, phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_devotee_email ON devotee(tenant_id, email) WHERE email IS NOT NULL;
