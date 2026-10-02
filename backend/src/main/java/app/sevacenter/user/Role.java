package app.sevacenter.user;

/** Roles within a tenant, highest privilege first. Stored as text (see V2 migration). */
public enum Role {
    TRUST_ADMIN,
    LEADER,
    MEMBER
}
