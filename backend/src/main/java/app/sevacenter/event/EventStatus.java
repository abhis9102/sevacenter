package app.sevacenter.event;

/** An event's lifecycle (ADR 0014): only PUBLISHED events are public and take registrations. */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
