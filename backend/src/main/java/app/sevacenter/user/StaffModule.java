package app.sevacenter.user;

/**
 * Areas of the staff app a TRUST_ADMIN can limit per user (ADR 0021). Staff management, payments
 * and the audit log aren't modules: they stay TRUST_ADMIN-only by role and can't be delegated.
 */
public enum StaffModule { DEVOTEES, DONATIONS, EVENTS, PUJAS, VOLUNTEERS, TEMPLE }
