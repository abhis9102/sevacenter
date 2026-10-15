# 0037 — Groups: satsang, bhajan, youth and study groups devotees can join

- **Status:** Accepted (2026-10-15)
- **Deciders:** AppSec (Abhi); built by Claude
- **Relates to:** ADR 0018 / 0025 (devotee login), ADR 0021 (module access)

## Context

Temples run ongoing groups: a Sunday satsang, a bhajan mandali, youth and mahila mandal groups,
study circles. Staff need to know who is in each; devotees want to find and join them.

## Decision

1. **Staff (SevaCenter, People → Groups):** a new `GROUPS` module (ADR 0021). MEMBER+ see the list;
   LEADER+ create and edit groups, see members with their contact, approve requests, add and
   remove members. Every staff action is audited.
2. **Devotees (MandirCenter `/community`):** only **listed** groups appear (active and PUBLIC). A
   signed-in devotee (their login code, ADR 0018) joins an OPEN group at once, or sends a request
   to an APPROVAL group. They can leave at any time.
3. **Private groups** are never listed and only staff add members, by the email or phone the
   devotee signs in with (they must have signed in once: no accounts are created for people).
4. **Privacy:** the public list shows a member *count* only. Member names and contacts are LEADER+
   and returned with `no-store`.
5. **Capacity** is checked under a lock on the group row, so concurrent joins can't overfill it.
6. Two tables with forced RLS: `community_group`, `group_member` (a member is a devotee account,
   unique per group). Paths: staff `/groups`, public `/community` (the brand gate routes by path).

## Consequences

- Devotees need a MandirCenter sign-in to join; until email/SMS delivery is live in production,
  that limits who can join.
- Tests: `GroupTest` (open and approval joins, capacity, private groups, counts without names,
  roles, cross-trust), frontend nav tests pin Groups under People.
