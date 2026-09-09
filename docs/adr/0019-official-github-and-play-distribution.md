# ADR 0019: Official GitHub and Google Play distribution

- Status: Accepted
- Date: 2026-09-09

## Context

Harnex needs a public distribution surface that does not make Google Play a prerequisite for trying or integrating the Host. At the same time, Google Play remains useful as an optional managed distribution channel.

Android package signing makes this a security and compatibility decision, not only a publishing convenience. The Play-delivered APK is signed by the Google Play App Signing identity. A GitHub-distributed APK cannot be presented as the same update lineage unless Harnex actually controls the same app-signing lineage. Reusing the Play upload key as a GitHub distribution key would also couple two otherwise independent trust domains and increase credential blast radius.

The public Consumer boundary already uses an explicit Host package/service component. Harnex authorization derives Consumer authority from Binder UID -> exact package -> current signer -> Harnex Control Plane authorization -> enabled use case. Host distribution channel does not weaken that boundary.

## Decision

### Two official channels

Harnex supports two official Android Host distribution channels:

1. **GitHub Releases** — the primary direct-download channel for release APKs.
2. **Google Play** — an optional managed distribution channel, initially through Internal Testing and later through any explicitly qualified public track.

Both channels use the same release application ID:

```text
io.github.daniele21.localllm.phonetest
```

Consumers therefore keep one explicit Host package/service contract.

### Independent signing identities

The GitHub APK uses a dedicated Harnex GitHub Release signing key. Google Play keeps its existing upload-key/App-Signing model.

The GitHub Release key:

- is never the Google Play upload key;
- is never committed to the repository;
- is restored only inside the protected release environment;
- has a stable certificate SHA-256 digest recorded in release metadata;
- is backed up and rotated only through an explicit release/security decision.

The Play upload key remains only an upload credential. Google Play App Signing remains the signer of Play-delivered APKs.

### Channel switching is explicit

Because the GitHub and Play APKs may have different signing lineages while sharing the same package name, Android must not be expected to install one as an in-place update over the other.

Switching channel may require uninstall/reinstall. Harnex must document that this can remove app-private model/configuration state. The project does not add signature spoofing, hidden package variants or automatic channel migration to conceal this Android invariant.

A future signing-lineage migration may remove this limitation only if it is explicitly designed, proven and compatible with the actual Play signing configuration.

### Candidate promotion, not rebuild-at-release

A GitHub Release is published from a previously prepared immutable release candidate:

```text
validated main commit
  -> build unsigned release APK
  -> sign with GitHub Release key
  -> verify signer + SHA-256
  -> generate release manifest / checksums / notes
  -> store bounded candidate artifact
  -> complete applicable REAL_ENVIRONMENT evidence against that exact APK
  -> publish the same bytes as the GitHub Release asset
```

The publish step must not rebuild the APK.

### Release identity

Each GitHub Release records at least:

- Harnex version and tag;
- exact `main` commit;
- Host package, versionName and versionCode;
- distribution channel;
- Host signer certificate SHA-256 digest;
- APK SHA-256 digest and size;
- Consumer SDK version;
- Binder protocol major/minor;
- pinned `llama.cpp` revision;
- release-evidence reference;
- checksums and build provenance.

Model digests and representative-device identities are attached when they are part of the release claim. Secrets, full certificates, prompts, outputs, private paths and GGUF bytes are never release metadata.

### Release classes

Pre-stable versions use Semantic Versioning prerelease identifiers such as `v0.5.0-rc.1` and are marked as GitHub prereleases. A prerelease does not relax the repository's RELEASE/FULL validation contract or permit unsupported production/device claims.

## Consequences

- Developers can obtain Harnex directly from GitHub without Google Play.
- Play remains optional and independently operable.
- The two channels can carry different signer digests without changing the Binder Consumer contract.
- Cross-channel in-place upgrade is not promised.
- Release assets become durable, immutable public distribution artifacts rather than seven-day CI artifacts.
- A public GitHub APK makes the GitHub signer and exact APK bytes part of release evidence.
- The first GitHub release cannot be published until the dedicated signing secret and applicable physical evidence exist for the exact prepared candidate.

## Validation requirements

Before publishing a GitHub APK:

- the exact source revision is a validated `main` commit;
- RELEASE/FULL automated gates are green for that source/build surface;
- the candidate APK is signed by the configured GitHub Release key;
- its signer digest, SHA-256 and source identity match `release-manifest.json`;
- the publish job downloads the prepared candidate rather than rebuilding it;
- applicable physical ARM64/JNI/GGUF and independently signed Consumer evidence is complete for the claim being published;
- release notes distinguish qualified behavior from candidate/known limitations;
- no signing material enters source, logs, artifacts or release assets.

Google Play publication continues to follow `docs/play-internal-phone-test.md` and the existing Play signing/runbook policy.

## Relationship to other decisions

- ADR 0004 remains authoritative for Google Play upload-key custody.
- ADR 0008 remains authoritative for `dev -> main` stable promotion.
- ADR 0018 remains authoritative for independently signed Consumer authorization.
- `docs/versioning.md` owns the durable version/release identity policy.
