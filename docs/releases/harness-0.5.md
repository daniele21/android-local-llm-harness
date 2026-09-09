# Harnex 0.5.0-rc.1 release checklist

Status: active
Document type: release-checklist
Owner: repository
Canonical scope: release.harness-0.5
Read when: preparing, validating or publishing the first Harnex 0.5 prerelease
Last reviewed: 2026-09-09

This file owns only the remaining release gates for the first Harnex GitHub prerelease target, `0.5.0-rc.1`. Current integrated repository state belongs in [`../current-state.md`](../current-state.md); durable version/distribution policy belongs in [`../versioning.md`](../versioning.md) and ADR 0020.

A GitHub prerelease is a real public release record. The `rc` label narrows stability expectations; it does **not** downgrade RELEASE/FULL validation or applicable REAL_ENVIRONMENT evidence.

## Release surface

The intended first public release surface is:

- GitHub Releases as the primary direct-download channel;
- one official `arm64-v8a` Harnex APK signed with the dedicated GitHub Release identity;
- Google Play as an optional independent channel, not a prerequisite for the GitHub Release;
- `release-manifest.json`, `release-record.json`, `SHA256SUMS` and build provenance;
- Consumer Android SDK compatibility identity and Binder protocol identity recorded independently from the Harnex version;
- release notes that separate qualified behavior from candidate/known limitations.

The GitHub and Play Host APKs may have different signing lineages while sharing the same package. Cross-channel in-place update is not promised and may require uninstall/reinstall.

## Repository release setup

- [x] Define the canonical Harnex repository version in root `VERSION`.
- [x] Define GitHub primary direct distribution + optional Play and independent signer semantics in ADR 0020.
- [x] Define prepare/publish immutable candidate lifecycle in `docs/versioning.md`.
- [x] Add the GitHub Release operational runbook.
- [x] Add release metadata/checksum generation and exact-byte verification tooling with focused tests.
- [x] Add the protected `GitHub Release` prepare/publish workflow.
- [x] Add build-provenance attestation for the public GitHub APK candidate.
- [ ] Merge the release-productization setup into the current `dev` line with required FULL automated validation.
- [ ] Confirm live repository rulesets protect `dev` and `main` as documented: PR-only changes, current branch requirement, required repository validation, no force push/deletion; `main` additionally requires approval.
- [ ] Enable automatic deletion of merged short-lived branches or otherwise reconcile the live repository setting with `BRANCHING.md`.

## Dedicated GitHub signing identity

- [ ] Create the dedicated PKCS12 GitHub Release signing key outside the repository.
- [ ] Store an encrypted offline backup and recovery information before first publication.
- [ ] Record the public signing-certificate SHA-256 fingerprint in the private release record.
- [ ] Configure the protected `github-release` GitHub Actions environment.
- [ ] Configure `HARNEX_GITHUB_RELEASE_KEYSTORE_B64`, store/key-password secrets and the release-key alias variable.
- [ ] Confirm no Play upload/app-signing private material is reused for the GitHub signing lineage.

The first GitHub APK must not be prepared or published using a transient/debug signing identity.

## Release source and promotion

- [ ] Reconcile the intended first-release scope into the exact current `dev` candidate; do not release a stale branch while material release content is pending elsewhere.
- [ ] Run RELEASE/FULL automated validation on the exact `dev` candidate against live `main`.
- [ ] Review the complete `dev -> main` diff and resolve all material release/documentation ambiguity.
- [ ] Promote the exact green `dev` candidate to `main` using the repository promotion contract.
- [ ] Confirm exact-main push `Validate` and `Package Android Artifacts` workflows are green.
- [ ] Confirm `VERSION` and the matching `CHANGELOG.md` release section remain `0.5.0-rc.1` on the exact release source.

No tag is created during promotion.

## Prepare the immutable GitHub candidate

Run `.github/workflows/github-release.yml` with `mode=prepare` only after the source/promotion gates above close.

- [ ] Prepare succeeds from the exact current `main` commit.
- [ ] Candidate APK is signed by the dedicated GitHub Release certificate.
- [ ] `release-manifest.json` records source revision, Host version/build, channel, signer digest, Consumer SDK, Binder protocol, `llama.cpp`, APK size and SHA-256.
- [ ] `SHA256SUMS` matches the exact prepared APK.
- [ ] GitHub build-provenance attestation exists for the exact APK.
- [ ] Record the prepare workflow run ID as part of release evidence.
- [ ] Verify no public tag or GitHub Release was created by prepare.

The candidate produced here is the artifact that must be exercised on-device. Rebuilding or resigning another APK invalidates exact-byte release evidence.

## Public shared-runtime / signer evidence

ADR 0018 remains authoritative for external Consumer authorization.

- [x] Deterministic distinct-signer Consumer-first authorization semantics are covered, including pending-before-authorization, explicit authorization, reconnect and replacement-signer denial.
- [x] Existing focused Play Internal evidence records the actual first-party pre-release topology truthfully; the observed Harnex/RedactGuard Play pair used the same Play App Signing digest and is **not** distinct-signer physical proof.
- [ ] For this first public GitHub Host release, run the applicable physical Consumer journey with the exact GitHub-signed Host candidate and a genuinely distinct external Consumer signing identity.
- [ ] Record exact Host and Consumer package/version/signing-certificate SHA-256 identities.
- [ ] Prove Consumer-before-Host reachability without reinstalling the Consumer.
- [ ] Prove denial while the source-observed identity is `PENDING`, then success only after exact Harnex authorization.
- [ ] Prove Connect -> Disconnect -> Reconnect and replacement/unknown signer denial where applicable.
- [ ] Keep signing keys/passwords/full certificates out of the evidence bundle.

If the first GitHub release deliberately makes no claim about a particular independently distributed Consumer, the release notes must narrow that claim explicitly rather than treating unrelated deterministic evidence as physical qualification.

## Representative Android runtime evidence

The exact release claim determines which physical evidence is blocking. The public APK is not allowed to turn emulator/host proof into a representative-device claim.

- [ ] Install the exact prepared GitHub APK on representative physical `arm64-v8a` hardware.
- [ ] Verify production JNI/`llama.cpp` loading from that exact APK.
- [ ] Install/select the exact curated Qwen3.5 artifact used for release qualification and record its digest/profile identity.
- [ ] Generate and stream through the real native backend.
- [ ] Cancel during applicable prefill/decode paths and record bounded cleanup behavior.
- [ ] Exercise load/generate/release/unload cycles without unbounded resource growth.
- [ ] Capture reviewable memory and thermal evidence when those dimensions are part of the published release claim.
- [ ] Record device manufacturer/model, Android version and ABI without persisting adb serial.
- [ ] Keep prompts, outputs, GGUF bytes, private paths, document URIs, signed URLs and signing secrets out of release evidence.

The reviewed Qwen3.5 4B 4-bit tier remains `CANDIDATE` until its own exact-artifact representative-device runtime/memory/thermal/output-quality gates close. Its presence in the catalog does not require `0.5.0-rc.1` to claim the tier as qualified.

## Product/API/release review

- [ ] Public Consumer API/ABI review is current for the exact release source.
- [ ] `samples/hello-harnex` remains buildable against the public Consumer SDK and the intended Host capability surface.
- [ ] Security review covers exported explicit Binder service, Binder-derived caller identity, exact package/signer authorization, bounded pre-authorization work and privacy boundaries.
- [ ] Release notes identify qualified versus candidate model/device behavior.
- [ ] Release notes state the GitHub/Play channel-switch reinstall limitation.
- [ ] Cache/persistence/upgrade compatibility decision is explicit for this candidate.
- [ ] Runtime-critical changes, if included since the last comparable evidence baseline, have the required benchmark/resource comparison.

## Optional Google Play channel

These gates are **not required to publish the GitHub Release** unless a release claim explicitly depends on Play.

- [ ] Optional: publish the corresponding candidate through Google Play Internal Testing.
- [ ] Optional: record the Play-delivered Host versionCode/versionName and Play App Signing digest.
- [ ] Optional: perform the Play-specific physical journey for claims about Play distribution.
- [ ] Optional: document that the Play APK is a separate signing/distribution lineage and is not byte-identical to the GitHub APK.

Play publication continues to follow [`../play-internal-phone-test.md`](../play-internal-phone-test.md).

## Publish `v0.5.0-rc.1`

Only after every blocking gate applicable to the public GitHub release claim is closed:

- [ ] Calculate and retain the SHA-256 of the reviewed privacy-safe physical evidence bundle.
- [ ] Run `.github/workflows/github-release.yml` with `mode=publish`, the exact prepare run ID and the reviewed evidence reference/SHA-256.
- [ ] Confirm publish downloads the prepared candidate rather than rebuilding it.
- [ ] Confirm the final `release-record.json` binds the exact candidate and evidence identity.
- [ ] Create tag `v0.5.0-rc.1` only on the validated `main` commit.
- [ ] Publish GitHub prerelease `v0.5.0-rc.1` with the exact APK, manifest, release record and checksums.
- [ ] Download the public APK after publication and independently verify SHA-256 and signing-certificate digest.
- [ ] Confirm the GitHub Release is marked prerelease and release notes do not exceed the evidence.
- [ ] Update `docs/current-state.md` only after the release actually exists.

## Required release identity

The final release record binds, when applicable:

```text
Harnex version + exact git commit
GitHub candidate workflow run ID
Host package + versionName/versionCode + distribution channel
Host signing-certificate SHA-256
public APK SHA-256 + size
Consumer package/version/signer identity for claimed external-consumer evidence
Consumer SDK version
Binder protocol major/minor + negotiated identity when exercised
selected curated Qwen3.5 model digest/profile identity
pinned llama.cpp revision
physical Android device/version/ABI
reviewed evidence reference + SHA-256
```

Full certificates, private keys and passwords are never release artifacts.

## Required procedures

- GitHub Release: [`../github-releases.md`](../github-releases.md)
- Versioning/distribution identity: [`../versioning.md`](../versioning.md)
- Build and emulator workflow: [`../android-build-and-run.md`](../android-build-and-run.md)
- ADB/device lifecycle: [`../device-e2e-testing.md`](../device-e2e-testing.md)
- Evidence format: [`../device-e2e-evidence.md`](../device-e2e-evidence.md)
- Shared-runtime release evidence: [`../shared-runtime/sr6-release-evidence.md`](../shared-runtime/sr6-release-evidence.md)
- Optional Play Internal Testing: [`../play-internal-phone-test.md`](../play-internal-phone-test.md)
- Play upload-key configuration: [`../android-upload-key.md`](../android-upload-key.md)
- Completion criteria: [`../definition-of-done.md`](../definition-of-done.md)

## Release decision

`v0.5.0-rc.1` is **not published yet**. Repository setup, signing custody, exact-source promotion, immutable candidate preparation and applicable REAL_ENVIRONMENT evidence must converge before publication. A green setup PR or a prepared candidate is not a release.
