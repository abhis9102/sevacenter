-- Per-module access limits for staff (ADR 0021). A limit only ever narrows what the user's role
-- allows (VIEW = read-only, NONE = no access); it can never grant more. Stored canonical, sorted,
-- e.g. 'DEVOTEES:VIEW,DONATIONS:NONE'; null = no limits. TRUST_ADMINs are never limited (the app
-- ignores and refuses limits on them), so a trust can't lock itself out.
ALTER TABLE app_user ADD COLUMN module_limits TEXT CHECK (module_limits ~
    '^(DEVOTEES|DONATIONS|EVENTS|PUJAS|VOLUNTEERS|TEMPLE):(VIEW|NONE)(,(DEVOTEES|DONATIONS|EVENTS|PUJAS|VOLUNTEERS|TEMPLE):(VIEW|NONE)){0,5}$');
