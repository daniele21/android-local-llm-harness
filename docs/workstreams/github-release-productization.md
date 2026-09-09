# GitHub release productization

Status: active
Document type: workstream
Owner: repository
Canonical scope: release.github-productization
Read when: implementing or resuming the first Harnex GitHub Release and dual-channel distribution setup

## Product intent

- **Product depth:** PRODUCT_STRATEGIC
- **User / problem / outcome:** Android developers need a direct, verifiable Harnex distribution path that does not require Google Play; Harnex maintainers need one release process that keeps GitHub and optional Play distribution truthful and auditable.
- **Non-goals:** no silent signer equivalence, no cross-channel in-place-update promise, no weakening of RELEASE/FULL or physical evidence, no publishing CI fixtures as user products.
- **Risks:** VALUE low-to-medium (direct install reduces evaluation friction); USABILITY medium (channel switching can require reinstall); FEASIBILITY medium (signing/provenance/exact-candidate promotion); VIABILITY low (existing GitHub/Play infrastructure is sufficient).
- **Material assumptions:** a dedicated GitHub signer can be kept stable and backed up; users can choose one distribution channel rather than switching in-place; first-public-release claims stay bounded to evidence actually completed.
- **Decision:** BUILD the smallest dual-channel model: same package/service contract, dedicated GitHub signer, independent Play signing, immutable candidate prepare/publish flow.
- **Success:** a validated `main` commit can prepare one signed GitHub APK with manifest/checksums/provenance; physical evidence can bind to that exact APK; publication reuses the same bytes; Play remains independently optional.
- **Post-release question:** does direct GitHub installation materially reduce onboarding/support friction for external Consumer developers? No new telemetry is required; issues/discussions and integration experience are sufficient initially.

## Invariants

- GitHub and Play signing credentials are separate.
- GitHub release assets are immutable and created only from validated `main`.
- Publish never rebuilds the APK prepared for evidence.
- Release identity records exact source, Host, SDK, Binder, backend, signer and artifact identities.
- GitHub prerelease does not imply universal device/model production readiness.
- REAL_ENVIRONMENT evidence remains blocking when required by `.engineering/e2e.json` and the release claim.

## Work graph

### GR-1 — Canonical release/distribution contract — ACTIVE

Owns/writes: `VERSION`, ADR 0020, `docs/versioning.md`, release runbook, release checklist, changelog/README release surface.

Acceptance: GitHub primary direct APK + optional Play are documented without signer/update ambiguity; `v0.5.0-rc.1` is the first target.

Validation: documentation/repository guards.

### GR-2 — Immutable GitHub candidate tooling — READY

Depends on: GR-1.

Owns/writes: release metadata generator/tests and `.github/workflows/github-release.yml`.

Acceptance: `prepare` builds/signs/verifies one exact APK and uploads manifest/checksums/notes; `publish` downloads that candidate, verifies identity/evidence inputs and creates the tag/GitHub prerelease without rebuilding.

Validation: Python tests, workflow syntax/static validation, Android release packaging, FULL due release/build-system surface.

### GR-3 — Repository OSS intake surface — READY

Owns/writes: `.github/ISSUE_TEMPLATE/**` and release-facing README links only.

Acceptance: structured bug/device/model/docs/feature reports collect Harnex/version/device/model/SDK context without requesting sensitive inference content.

Validation: repository/docs guards.

### GR-4 — First release candidate — BLOCKED

Depends on: GR-1, GR-2 integrated to `dev`, then RELEASE/FULL `dev -> main` promotion.

External requirements:

- protected GitHub Release environment contains the dedicated signing credential;
- exact GitHub candidate APK has required representative-device evidence;
- public distinct-signer distribution claim has exact Host/Consumer signer evidence per ADR 0018;
- branch/ruleset admin settings satisfy the documented repository protection contract.

Acceptance: public GitHub prerelease `v0.5.0-rc.1` exists with the exact prepared APK, `release-manifest.json`, `SHA256SUMS`, release notes and provenance; Play remains optional and separate.

## Durable docs affected

- `docs/versioning.md`
- `docs/releases/harness-0.5.md`
- `docs/current-state.md`
- `README.md`
- `CHANGELOG.md`
- `docs/adr/0020-official-github-and-play-distribution.md`

## Resume checkpoint

Base at workstream start: `dev@d60c0ff9560d6eed225e4fd6e02e746f18625935`.

Confirmed facts: `main` is stable/release; GitHub Releases are empty; package CI already creates bounded artifacts; Play Internal uses Google Play App Signing and a separate developer upload key; Consumer Host package/service is explicit; ADR 0018 permits independently signed consumers.

Remaining release obligations: dedicated GitHub signing configuration, exact candidate automated validation, exact GitHub APK physical evidence, public distinct-signer evidence, release publication.

Next discriminating action: integrate and validate GR-1/GR-2, then prepare the exact GitHub-signed candidate rather than generating a release from an unqualified rebuild.
