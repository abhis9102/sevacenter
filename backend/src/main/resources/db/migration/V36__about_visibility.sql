-- Show or hide the About and History sections (text and their photos) on MandirCenter.
ALTER TABLE temple_profile ADD COLUMN show_about   BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE temple_profile ADD COLUMN show_history BOOLEAN NOT NULL DEFAULT true;
