<!-- Keep PRs small and focused. Title in Conventional Commits form, e.g. `feat(donations): add Razorpay webhook`. -->

## What & why

<!-- What does this change do, and why? Link the issue/milestone. -->

## AI-assisted code declaration

> SevaCenter is built with coding agents. Provenance is an audit trail, not a security
> control — but we declare it honestly.

- [ ] This PR contains AI-generated or AI-assisted code
- **Tool / model (if any):** <!-- e.g. Claude Code / claude-opus, Antigravity -->
- [ ] I (the human author) have read and understood every line I'm submitting

## Security self-check (AppSec gate)

- [ ] Touches a **sensitive path** (auth, authorization, tenancy/RLS, payments, IaC/IAM)? If yes, flag for focused review.
- [ ] No secrets, keys, or credentials added (gitleaks/trufflehog will also check)
- [ ] Authorization enforced **server-side** for any new endpoint (never trust the client/UI)
- [ ] New/changed queries respect tenant isolation (RLS / `tenant_id` scoping)
- [ ] Input validated; errors don't leak internals
- [ ] New dependencies are real, pinned, and necessary (SCA will also check)

## Tests

- [ ] Added/updated tests; `./mvnw verify` passes locally
