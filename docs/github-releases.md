# GitHub Releases

Status: active
Document type: runbook
Owner: repository
Canonical scope: release.github
Read when: preparing, evidencing, publishing or verifying an official Harnex GitHub Release
Last reviewed: 2026-09-09

GitHub Releases are Harnex's primary direct-download Android distribution channel. Google Play remains an optional independent channel. The durable policy is [`versioning.md`](versioning.md); ADR 0020 owns the two-channel signing/distribution decision.

## Distribution identity

The GitHub APK uses the production Host package and service contract:

```text
package: io.github.daniele21.localllm.phonetest
service: io.github.daniele21.localllm.phonetest.HarnessSharedRuntimeService
```

It is signed with a dedicated GitHub Release signing identity. The Google Play upload key and Google Play App Signing identity are separate and must not be reused or represented as equivalent.

Because Android requires signer continuity for an in-place update, switching between a GitHub-signed APK and a Play-signed APK with the same package can require uninstall/reinstall. Uninstalling can remove Harnex app-private configuration and installed-model state. A release or support instruction must state this explicitly rather than suggesting a seamless channel switch.

## One-time GitHub signing setup

Create the GitHub Release keystore outside the repository on a trusted maintainer machine:

```bash
mkdir -p ~/.keystore
keytool -genkeypair -v \
  -storetype PKCS12 \
  -keystore ~/.keystore/harnex-github-release.p12 \
  -alias harnex-github-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Use a strong unique password. Do not reuse the Play upload-key password. Keep an encrypted offline backup of the PKCS12 file and recovery information before publishing the first release; losing this key breaks the GitHub APK update lineage.

Record the public signing identity without exporting private material:

```bash
keytool -list -v \
  -keystore ~/.keystore/harnex-github-release.p12 \
  -alias harnex-github-release
```

The certificate SHA-256 fingerprint is public release identity evidence. The keystore, private key and passwords are secrets.

## Protected GitHub environment

Create a GitHub Actions environment named:

```text
github-release
```

Restrict deployment to trusted release maintainers and stable release refs according to repository policy. Configure:

| Type | Name | Purpose |
| --- | --- | --- |
| Secret | `HARNEX_GITHUB_RELEASE_KEYSTORE_B64` | Base64-encoded PKCS12 keystore |
| Secret | `HARNEX_GITHUB_RELEASE_STORE_PASSWORD` | PKCS12 password |
| Secret | `HARNEX_GITHUB_RELEASE_KEY_PASSWORD` | Key password; may equal store password but is stored independently |
| Variable | `HARNEX_GITHUB_RELEASE_KEY_ALIAS` | Normally `harnex-github-release` |
| Variable | `HARNEX_GITHUB_RELEASE_SIGNER_SHA256` | Pinned expected public certificate SHA-256 digest, normalized as 64 lowercase hex characters |

Prepare and publish both fail closed if the actual APK signer differs from `HARNEX_GITHUB_RELEASE_SIGNER_SHA256`. Updating that variable is therefore a signing-lineage change, not routine release metadata.

To create the base64 value locally without changing the keystore:

```bash
base64 < ~/.keystore/harnex-github-release.p12 | tr -d '\n'
```

Never paste the resulting value into issues, PRs, logs, release notes or repository files.

## Native GitHub release immutability

Before the first public Harnex release, enable repository release immutability in GitHub repository settings. This is an administrative repository setting and is not replaced by the workflow's exact-byte checks.

With release immutability enabled, a published release's tag and assets cannot be modified or deleted. Draft releases remain editable until publication. The GitHub CLI release-create flow stages asset uploads before publication, so the canonical workflow can attach the complete release surface and only then cross the immutable publication boundary.

Build-provenance attestation for the prepared APK remains separate and complementary: provenance binds how the candidate was produced, while GitHub release immutability protects the published tag/assets after publication.

## Release flow

The canonical workflow is `.github/workflows/github-release.yml` and has two deliberate phases.

### 1. Integrate and promote the release source

The intended release content first reaches `dev` through normal integration. The exact candidate is then promoted from `dev` to `main` under the repository RELEASE/FULL contract.

Do not create a release tag on `dev` and do not prepare a public APK from a feature branch.

### 2. Prepare the immutable GitHub candidate

Run **GitHub Release** manually with:

```text
mode: prepare
version: <exact VERSION value>
```

Prepare fails closed unless:

- the checkout is the exact current `main` commit;
- `VERSION` matches the requested version;
- the release tag/release does not already exist;
- exact-main `Validate` and `Package Android Artifacts` push workflows are green;
- the dedicated signing material and pinned signer digest are available through the protected environment.

The job builds an unsigned release APK, signs it once with the GitHub Release key, verifies the signature against the pinned certificate digest, generates release metadata and creates build provenance.

The bounded candidate artifact contains only the public release surface:

```text
harnex-v<version>-android-arm64.apk
release-manifest.json
SHA256SUMS
release-notes.md
```

The workflow run ID is part of release evidence. No public tag or GitHub Release exists yet.

### 3. Qualify the exact prepared APK

Use the exact prepared APK for all REAL_ENVIRONMENT evidence required by the release claim. Do not rebuild or locally resign a substitute and call it equivalent.

At minimum, the evidence record for a public shared-runtime GitHub APK must bind:

```text
candidate workflow run ID
Harnex version and exact main commit
GitHub APK SHA-256
GitHub Host signing-certificate SHA-256
Host package/version
Consumer package/version/signing-certificate SHA-256 when applicable
Consumer SDK version
Binder protocol identity
selected curated model digest when runtime evidence applies
pinned llama.cpp revision
physical Android device/version/ABI when physical evidence applies
```

For independently signed consumer claims, follow [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md) and ADR 0018. The Host and external Consumer signer identities must be genuinely distinct for the public distinct-signer claim.

Representative ARM64/JNI/GGUF/memory/thermal evidence remains claim-specific. Emulator evidence is never relabelled as physical evidence.

Bundle or otherwise identify the reviewed privacy-safe evidence, calculate its SHA-256 and retain the reviewable reference. Evidence must exclude prompts, generated output, signing secrets, full certificates, GGUF bytes, Binder tokens, private model paths and device serial numbers.

### 4. Publish the same bytes

Run **GitHub Release** manually again with:

```text
mode: publish
version: <same VERSION value>
candidate_run_id: <prepare workflow run ID>
physical_evidence_ref: <reviewable evidence reference>
physical_evidence_sha256: <64-hex evidence bundle SHA-256>
```

Publish downloads the exact prepared candidate. It does not rebuild the APK. It verifies:

- candidate version;
- exact source revision and current `main`;
- APK filename, size and SHA-256;
- APK signer digest against the pinned expected certificate;
- required evidence identity.

It then finalizes `release-record.json`, creates the `v<version>` tag and publishes the GitHub Release with:

```text
harnex-v<version>-android-arm64.apk
release-manifest.json
release-record.json
SHA256SUMS
```

When native GitHub release immutability is enabled, the publication boundary makes the release tag/assets immutable. A prerelease version such as `0.5.0-rc.1` is published with GitHub's prerelease flag.

## Release notes

`CHANGELOG.md` is the human change source. Before prepare, the exact `VERSION` must have a non-empty `## [<version>]` section covering:

- highlights and material behavior changes;
- distribution/compatibility changes;
- breaking changes or migration when applicable;
- known limitations;
- qualified versus candidate model/device claims;
- current Consumer SDK identity when relevant.

Do not describe a prepared candidate as released. The release exists only after the publish phase succeeds.

## Google Play is optional

Play publication remains governed by [`play-internal-phone-test.md`](play-internal-phone-test.md) and [`android-upload-key.md`](android-upload-key.md). A GitHub Release can exist without a Play publication unless the specific release claim explicitly requires Play evidence.

If the same Harnex version is also distributed through Play, release notes should identify Play as a separate channel with a separate App Signing identity. Do not claim the GitHub APK and Play-delivered APK are byte-identical or signer-identical.

## Failure and retry rules

- If prepare fails, fix the underlying source/configuration and create a new exact source candidate; do not manually patch the APK.
- If physical evidence fails, diagnose the violated invariant. Do not publish and do not weaken the gate.
- If publish fails before the tag exists, retry against the same candidate only after confirming source and evidence identity are unchanged.
- If the tag exists but no GitHub Release exists, retry is allowed only when the tag resolves to the exact manifest source commit.
- If a GitHub Release already exists, never overwrite its assets. A changed public artifact requires a new version.

## Verification after publication

After publish:

1. verify the GitHub Release tag resolves to the recorded source revision;
2. download the public APK and compare it with `SHA256SUMS`;
3. verify its signing certificate digest with `apksigner verify --print-certs`;
4. confirm `release-record.json` identifies the reviewed candidate/evidence;
5. verify the release is marked prerelease/stable consistently with `VERSION`;
6. confirm the README/changelog do not claim broader compatibility than the evidence record.

Only then can the milestone be reported as a published GitHub Release.
