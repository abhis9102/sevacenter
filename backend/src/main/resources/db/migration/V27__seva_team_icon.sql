-- Each seva team shows an icon the temple picks from a fixed set (ADR 0028). The API allows only
-- the listed names; the database keeps the shape honest.
ALTER TABLE seva_team ADD COLUMN icon TEXT CHECK (icon ~ '^[a-z][a-z-]{1,23}$');
