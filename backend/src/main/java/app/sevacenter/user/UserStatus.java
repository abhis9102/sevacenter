package app.sevacenter.user;

/** Lifecycle of a staff account. Stored as text (see V4 migration). */
public enum UserStatus {
    /** Created by an admin; no password until the user completes their one-time setup link. */
    PENDING,
    ACTIVE,
    /** Offboarded: can't log in, and existing sessions end on their next request. */
    DISABLED
}
