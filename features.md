# CryptEnv — Remaining Features

Based on a read of the repository at commit `b6bf7e0` (`cryptenv-core`, `cryptenv-dashboard`, `cryptenv-cli`, `cryptenv-vscode`, `cryptenv-sdk/node`, `cryptenv-sdk/java`).
Target: parity with **SecretVault** (server-side control plane) and **XtraSecurity** (zero-knowledge, CLI-first) using **free / open-source tools only**.

Only unbuilt or incomplete work is listed. `(partial)` marks something that exists but is incomplete; the note says what is missing.

**Free-only substitutions:** Ollama instead of Gemini · local key provider (Argon2id/PBKDF2 KEK) instead of AWS KMS · Redis/Valkey in Docker · MailHog / Brevo / Resend free tier for email · Prometheus + Grafana · GitHub Actions, Vercel (hobby), Cloudflare Workers, Kubernetes (kind) as sync targets. AWS is optional, free tier only.

---

## 0. Fix First — Defects in Existing Code

These are bugs or weaknesses in things already built. Several block later features.

- [x] **Rotation overwrites real secrets with junk.** `SecretRotationScheduler.generateNewSecretValue()` returns `"rotated_" + System.currentTimeMillis()`. Any secret with `autoRotate=true` gets its value replaced by that placeholder. Disable auto-rotate until real generators exist (see §5). `rotateSecret` is also a `protected @Transactional` method called from within the same class, so the annotation has no effect.
- [x] **Audit cleanup deletes the wrong logs.** `AuditLogService.deleteOldLogs(beforeDate)` calls `findByTimestampAfter` and deletes those rows, so it removes the newest logs. Use a `timestamp < cutoff` bulk delete.
- [x] **Roles are global per user, not per workspace.** `User.role` is a single enum; `inviteMember` adds a user with no workspace-scoped role. A user who is `OWNER` in one workspace is effectively `OWNER` everywhere. Needs a `workspace_members(workspace_id, user_id, role)` model.
- [x] **`SecurityService.isOwner()`, `isAdmin()`, `isDeveloper()` are identical** (all return true for OWNER/ADMIN/DEVELOPER), so they do not distinguish roles.
- [x] **API keys are stored in plaintext** (`ce_live_<uuid>`, looked up by equality) and grant the user's full access. Store a SHA-256 hash plus a visible prefix, show the key once, add scopes and expiry.
- [x] **Workspace key derivation has no KDF.** `EncryptionService.deriveKey` pads or truncates the UTF-8 passphrase to 32 bytes, so a 16-character key has 16 zero bytes. Move to Argon2id or PBKDF2 with a per-workspace salt, use a versioned ciphertext format, and add a migration path that stays compatible with the Node/Java SDKs.
- [x] **No AAD binding.** GCM ciphertext is not bound to `secretId:envId:version`, so a ciphertext can be moved between rows undetected.
- [x] **`MasterKeyService` is dead code using `AES/ECB`.** Nothing references it. Delete it.
- [x] **Rate limiter trusts `X-Forwarded-For` blindly** (spoofable) and never evicts entries from its in-memory map. Add a trusted-proxy setting and bucket eviction; move to Redis for multi-instance.
- [x] **JWTs last 24 h with no refresh and no revocation.** `/auth/logout` only writes an audit entry; the token stays valid.
- [x] **`docker-compose.yml` is inconsistent.** Postgres runs as user `postgres` while the healthcheck and app use `cryptenv`; `MASTER_ENCRYPTION_KEY` is missing (the app will not start); passwords are hard-coded; no dashboard service; the app mounts source over `/app`.
- [x] **CI only builds `cryptenv-core`.** The workflow does not build or test the dashboard, CLI, SDKs or extension, and has no dependency or secret scanning.
- [x] **Licence mismatch.** README says Apache 2.0; `cryptenv-vscode/package.json` says MIT. README versions (CLI 1.3.0, VS Code 1.2.1) also differ from the manifests (VS Code 1.4.0).
- [x] `secrets.value` and `secrets.encrypted_value` hold the same ciphertext. Drop one.

---

## 1. Authentication & Identity

- [ ] Email OTP verification at registration (resend-safe upsert)
- [x] Refresh tokens with rotation and reuse detection; short-lived access tokens (15 min)
- [x] Token revocation / session list and "sign out everywhere" (Redis or DB denylist)
- [x] TOTP MFA (RFC 6238) with encrypted seed and recovery codes
- [ ] WebAuthn / FIDO2 passkeys with counter replay protection
- [x] **Step-up MFA** (5-minute token) for reveal, delete, rotate, production writes
- [ ] Migrate BCrypt to Argon2id with rehash-on-login
- [x] Account lockout and progressive delay (the `accountNonLocked` flag exists but nothing sets it)
- [x] Forgot / reset password flow
- [ ] OAuth login: GitHub, Google
- [ ] CLI device-code login (browser approval) instead of password prompt

## 2. Workspaces, Projects, Environments & Access Control

- [x] **Project layer**: Workspace → Project → Environment (currently Workspace → Environment)
- [x] Custom environments (`EnvironmentType` is a fixed enum of DEV/STAGING/PROD)
- [x] Per-workspace member roles (see §0); add `VIEWER`
- [x] Invitation flow with 256-bit token, 7-day expiry, email-locked acceptance (currently `inviteMember` adds an existing user directly)
- [ ] Member removal, role change, leave workspace
- [ ] Sole-owner invariant and ownership transfer
- [x] Protected environments (`is_protected`): writes need ADMIN or active JIT grant
- [ ] Project- and environment-scoped grants that can only narrow, never elevate
- [ ] IDOR guards on every path: verify workspace → project → environment → secret ownership, return 404 on mismatch
- [x] **JIT access**: request with justification, 1–24 h, approval, auto-expiry job, `cryptenv jit` / `jit-run`
- [ ] Quarterly access-certification reviews
- [ ] Secret branches: branch, diff, merge secret sets (XtraSecurity `status` / active branch)
- [ ] Permission matrix tests per role (`RBACIntegrationTest` exists; extend to workspace-scoped roles)

## 3. Cryptographic Engine

- [x] **Envelope encryption**: per-version random DEK wrapped by KEK; store wrapped DEK, IV, tag, algorithm/version tag
- [x] AAD binding (see §0)
- [x] `KeyProvider` interface (local Argon2id/PBKDF2 now; OpenBao/Vault Transit later)
- [ ] Master-key rotation that re-wraps every workspace key automatically (today each workspace key must be re-set by hand)
- [ ] Zeroize key and plaintext buffers after use
- [x] SHA-256 content fingerprint per version (needed for drift)
- [ ] **Zero-knowledge mode** per workspace: client-side AES-256-GCM, Argon2id passphrase KDF, server stores ciphertext only. Today the server holds the workspace key (wrapped) and returns plaintext from `/api/secrets`, so only the SDK path is client-side
- [ ] Secure-by-default key recovery story for ZK mode (print recovery key once)

## 4. Secret Reveal Protection

- [x] Reveal endpoint gated by step-up token
- [x] `Cache-Control: no-store` on all secret responses
- [ ] GCM tag failure → reject + security event
- [ ] Dashboard: mask by default, auto-hide timer, clipboard auto-clear
- [x] Dedicated `SECRET_REVEALED` audit entry with IP and user agent, separate from ordinary reads

## 5. Secret Lifecycle

Built: versions, rollback, soft delete/restore, activate/deactivate, expiry, rotation interval and auto-rotate flags.

- [x] **Real rotation (partial)**: replace the placeholder generator (§0) with generators: random password, hex/base64 token, UUID, RSA/Ed25519 keypair
- [ ] Shadow rollout with configurable grace window (old and new valid together)
- [ ] Blast-radius preview before rotation
- [ ] Rotation history view and failure alerts
- [ ] Expiry warnings (7 / 1 day) via notifications; currently expired secrets are only deactivated
- [ ] Version diff view (dashboard and CLI)
- [ ] Secret types: multiline, JSON, certificate, SSH key; tags
- [x] Import / export: `.env`, JSON, YAML, Docker secrets, Kubernetes `Secret`
- [ ] Secret references (`${OTHER_KEY}`) and cross-environment inheritance
- [x] Bulk create/update endpoint

## 6. Audit Ledger

Built: audit log entity, filtered list endpoints, dashboard page, 44 call sites.

- [x] **SHA-256 hash chain** (each row stores previous hash) and a verify endpoint
- [ ] Workspace-scoped audit queries (current endpoints filter by user, action, resource only)
- [ ] Log MFA events, permission changes, invitations, API-key use, rotations
- [ ] Export CSV / JSON; retention policy (after fixing §0)
- [ ] Make rows append-only at DB level (revoke UPDATE/DELETE, trigger)

## 7. Machine Identities & CI/CD

Built: per-user API key; `.github/workflows` builds the core only.

- [ ] Machine identities separate from user accounts; scoped, expiring, revocable
- [x] API keys: hashed storage, scopes, expiry, last-used, rotate
- [ ] Keyless **OIDC federation** for GitHub Actions, GitLab CI, Kubernetes service accounts
- [ ] `cryptenv oidc-link` command
- [ ] **Workload key envelope** (ZK CI): X25519 keypair per workload, project key wrapped to its public key
- [ ] Official GitHub Action (`cryptenv/setup`) that injects secrets without `.env`
- [ ] Short-lived machine tokens with a 15 s rotation grace window

## 8. Provider Sync

- [x] `SecretProvider` interface (`provider/SecretProvider.java`, registry-backed)
- [x] Adapters (free first): GitHub Actions secrets, Vercel env vars, Cloudflare Workers/Pages secrets, Kubernetes `Secret`; Docker/Compose export; AWS Secrets Manager optional *(AWS left out — free-only scope)*
- [x] Queue and worker; exponential backoff with jitter; dead-letter queue with replay *(DB-backed `provider_sync_jobs` + `@Scheduled` worker instead of Redis)*
- [ ] Redis `SETNX` lock per secret + provider *(single worker claims jobs transactionally; not required yet)*
- [x] Provider credential storage (encrypted with master key) and health checks
- [x] Sync status API and dashboard page

## 9. Drift Detection & Compliance

- [x] Fingerprint-only drift scan; classify `STALE` / `MODIFIED` / `MISSING`
- [x] Scheduled scans and optional auto-remediation
- [x] Attestation report; CSV / JSON evidence export (PDF not supported)
- [ ] VS Code drift indicator

## 10. Events, Automation, Webhooks, Notifications

Nothing built. The Notifications page is static.

- [ ] Transactional outbox for domain events
- [ ] SSE live feed to the dashboard; event replay
- [x] Automation rules (`ON event WHERE cond DO action`) with loop guard (max 3 fires / 60 s)
- [x] Four-eyes approval for destructive actions
- [ ] Outbound webhooks: HMAC-SHA256 signing, retries, delivery log, redelivery
- [ ] Anti-SSRF: block private/link-local ranges, pin resolved IP to defeat DNS rebinding
- [x] Channels: email (SMTP), Slack / Discord webhook, in-app; per-user preferences

## 11. Incident Management

- [ ] Incidents (`INC-####`) with lifecycle OPEN → INVESTIGATING → CONTAINED → RESOLVED → POST-MORTEM
- [ ] One-click quarantine (suspend machine identities, lock environment, queue rotations)
- [ ] Auto-attach audit entries and affected versions; timeline and post-mortem template

## 12. Leak Detection

- [ ] Scanner: regex rules, provider signatures, Shannon entropy (> 4.5 bits/char)
- [ ] `cryptenv scan <path>` and `scan --staged` plus pre-commit hook installer
- [ ] GitHub push-webhook scanning in a temp sandbox
- [ ] Allowlist and ignore comments
- [ ] VS Code live scanning and diagnostics
- [ ] Leak finding → rotation suggestion / automation rule

## 13. Security Posture

Built: `SecurityHealth` page lists fixed "pass" items; only secret and workspace counts are live.

- [ ] Real posture score (0–100) from secret age, unrotated secrets, MFA adoption, expired/inactive secrets, drift, leaks
- [ ] Stale / unused secret reports
- [ ] Blast-radius graph
- [ ] Prioritised remediation list

## 14. AI Security Copilot (Ollama)

- [ ] `AiProvider` interface: Ollama (local), offline rule-based fallback, optional Gemini free tier
- [ ] **Zero-plaintext firewall** on prompts, tool I/O and replies (redact to `[SHA256:prefix]`)
- [ ] RBAC-checked tools (`secrets.summary`, `rotation.schedule`, `drift.scan`, `user.getCurrent`)
- [ ] Bounded agent loop (≤ 5 turns, ≤ 10 tool calls, cycle detection)
- [ ] Remediation plans gated by step-up MFA and four-eyes approval
- [ ] Conversation history, SSE streaming, `cryptenv ai` command, dashboard chat panel

## 15. CLI (`cryptenv-cli`)

Built: `init`, `register`, `login`, `logout`, `profile`, `secrets ls|get|set|delete`, `workspaces ls|create`, `run` (token stored in OS keyring via `keytar`).

- [ ] **Environment selection**: `run` and `secrets` call `/api/secrets` with no workspace/environment flag; add `--workspace`, `--env`, `.cryptenv.json` resolution
- [ ] Secret update (`secrets set` creates only), `secrets import|export`, `secrets versions|rollback`
- [ ] Rotation, expiry and lifecycle commands (endpoints exist, CLI does not call them)
- [ ] `project set` and passphrase handling for ZK mode, cached in the keyring
- [ ] `status`, `doctor`, `whoami`, `ui` (terminal dashboard)
- [ ] `jit`, `jit-run`, `oidc-link`, `scan`, `drift`, `ai`
- [ ] Headless fallback when no keyring (machine-bound encrypted file; `keytar` is a native dependency)
- [ ] Shell completion; `--json` output; non-zero exit codes for CI
- [ ] Tests (the CLI has none)

## 16. SDKs

Built: Node SDK (login, init, get, getAll, load, setSecret, deleteSecret) and Java SDK (init, refresh, get, getAll) with client-side decryption.

- [ ] **Python SDK** (PyPI)
- [ ] Spring Boot starter (`PropertySource` auto-config) for the Java SDK
- [ ] Java SDK: write operations (`setSecret`, `deleteSecret`); Node has them
- [ ] Machine-token and OIDC auth in all SDKs
- [ ] Background refresh with TTL cache and change callbacks
- [ ] ZK-mode decryption (`CRYPTENV_VAULT_PASSPHRASE`) and key-format versioning (§0)
- [ ] Align package versions and READMEs (Node SDK `package.json` is 1.0.0, README says 1.1.0)

## 17. VS Code Extension

Built: sign in, API key, workspace/environment/secret CRUD, preview, copy, insert key/value.

- [ ] Hover decryption of `process.env.X` / secret references
- [ ] Live secret scanning with diagnostics
- [ ] Secret-name auto-completion
- [ ] Drift and expiry indicators
- [ ] Passphrase command for ZK mode (stored via VS Code `SecretStorage`)
- [ ] Rotation / version history / rollback commands
- [ ] Tests beyond the two existing smoke files

## 18. Dashboard

Built: landing, docs, login/register, overview, workspace, members, secrets list/editor, rotation page, audit log, settings, usage analytics.

- [ ] **Wire or remove the static pages**: `AccessRequests`, `AccessReviews`, `Integrations`, `Notifications`, `Subscription` use hard-coded arrays and are not routed in `App.jsx`
- [ ] Rotation page calls the lifecycle and version APIs (it currently does not)
- [ ] Version history, diff and rollback UI
- [ ] MFA / passkey setup and step-up modal
- [ ] Project and environment tree navigation
- [ ] JIT request and approval screens
- [ ] Sync / DLQ / drift pages; incident centre; live event feed
- [ ] Webhook manager and delivery inspector
- [ ] Machine identity and API-key management pages
- [ ] Posture / risk / blast-radius views; AI chat panel
- [ ] Dashboard tests (none exist)

## 19. Platform & DevOps

- [x] Fix `docker-compose.yml` (§0) and add dashboard, Redis, MailHog services
- [x] Redis-backed rate limiting (fail-closed) and trusted-proxy handling
- [ ] Security headers (CSP, HSTS, `X-Content-Type-Options`, frame options); stricter CORS
- [ ] Spring Actuator + Prometheus metrics + Grafana dashboards
- [ ] Structured JSON logs with secret-redaction filter
- [ ] PostgreSQL backup/restore script
- [ ] Integration tests with Testcontainers (current tests use H2, which hides Postgres-specific migrations such as the partial unique index in V15)
- [x] CI for all packages; dependency and secret scanning; SBOM
- [ ] Helm chart / Kubernetes manifests
- [ ] Upgrade to Java 21 (currently 17) and Spring Boot 3.3+; remove Flyway enable/disable indirection if no longer needed
- [ ] Docs folder: architecture, API reference, threat model, security policy (`docs/` only holds a screenshot)

---

## Suggested Order

| Phase | Scope | Why |
|---|---|---|
| **0** | §0 critical fixes (rotation placeholder, audit delete, compose, API-key hashing) | Prevents data loss and unblocks local dev |
| **1** | Per-workspace roles, Project layer, envelope encryption + AAD, hash-chained audit | Foundation for everything below |
| **2** | Refresh tokens, OTP, TOTP, step-up, reveal gating, JIT, invitations | Makes protected environments meaningful |
| **3** | Real rotation + generators, versions diff, import/export, CLI parity, Python SDK, VS Code upgrades | Visible developer value |
| **4** | Outbox + events, webhooks, notifications, provider SPI + queue, drift | Needs phases 1–2 |
| **5** | Machine identities, OIDC, GitHub Action, zero-knowledge mode, workload envelopes | Large cross-cutting change; do once server-managed path is stable |
| **6** | Incidents, leak scanner, posture score, automation rules | Builds on events and audit |
| **7** | Ollama copilot, dashboard completion, observability, Helm | Last |

## Definition of Done (per feature)

- [ ] Flyway migration, entity, repository
- [ ] Service enforces workspace → project → environment ownership and role checks
- [ ] Audit event (and outbox event where relevant)
- [ ] Unit tests plus a Testcontainers integration test
- [ ] CLI / SDK / dashboard surface updated
- [ ] Docs updated; no plaintext in logs, responses or caches