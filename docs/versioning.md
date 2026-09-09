# Versioning and release policy

Status: active
Document type: release-policy
Owner: repository
Canonical scope: release.versioning
Read when: changing versions, compatibility promises, promotion or release artifacts
Last reviewed: 2026-09-09

## Harnex repository versions

The repository-level Harnex version is stored in the root [`VERSION`](../VERSION) file and follows Semantic Versioning.

Before `1.0.0`, public Harnex milestones use prerelease identifiers when the supported envelope is still intentionally pre-stable, for example `0.5.0-rc.1`. The Git tag is the version prefixed with `v`.

A GitHub prerelease is a real immutable release record, not a development snapshot. It may communicate a bounded pre-stable support envelope, but it does not relax RELEASE/FULL validation or permit claims that exceed the attached evidence.

## SDK versions

Published Android and Capacitor artifacts follow Semantic Versioning.

Before `1.0.0`, minor releases may contain deliberate API changes, but every breaking change must include migration notes. Patch releases must remain backward compatible within the same minor line.

The shared-runtime Android client artifact has its own SDK identity. Development builds use snapshot semantics; a distributable client version is not inferred from the host application version or Binder protocol version.

## Independent identities

The following identities are versioned or recorded separately from the SDK release:

- Harnex repository/version identity;
- shared-runtime host application version/build identity;
- shared-runtime Android client SDK version;
- shared-runtime Binder protocol major/minor and negotiated feature set;
- Host and consumer package/signing-certificate identities;
- Host distribution channel and release signer identity;
- GGUF artifact digest;
- model load profile schema;
- use-case profile schema;
- app binding schema;
- health suite definition;
- benchmark definition;
- diagnostics protocol;
- pinned `llama.cpp` commit.

Changing an SDK version must never implicitly change an application's configured model identity. Changing the host version must not silently redefine the Binder compatibility contract. Changing a compatible Binder minor does not, by itself, require the host and client package versions to match.

## Shared-runtime compatibility identity

A shared-runtime release or physical evidence record must identify at least:

```text
harnex version + git commit
host package + version/build + distribution channel
consumer package + version/build when a concrete app is under test
client SDK version
Binder protocol major/minor
negotiated protocol minor/features when execution evidence exists
host signing-certificate digest identity
consumer signing-certificate digest identity
selected curated model digest
pinned llama.cpp revision
Android device/version/ABI for physical evidence
```

The Binder protocol remains independently versioned from host/client packaging. Major incompatibility fails before registration; compatible minor differences negotiate the common feature set. The protocol fixture policy is owned by the shared-runtime contract documentation rather than Semantic Versioning of either APK.

Signing certificate digests are evidence/security identities, not product versions. Independently distributed Host and consumer applications are not required to share a signing identity; authorization follows ADR 0018. Full certificates, private keys and passwords are never release metadata.

## Official Android distribution channels

Harnex has two official Host distribution channels, as defined by ADR 0019.

### GitHub Releases

GitHub Releases are the primary direct-download channel. The public APK:

- uses package `io.github.daniele21.localllm.phonetest`;
- is signed with a dedicated Harnex GitHub Release key;
- records the signing-certificate SHA-256 digest in release metadata;
- is prepared once, validated/evidenced as that exact byte sequence and then promoted without rebuilding;
- is published with `release-manifest.json`, `release-record.json`, `SHA256SUMS` and GitHub build-provenance attestation.

The GitHub Release signing key is not the Google Play upload key and is not committed or emitted as an artifact.

### Google Play

Google Play is an optional managed distribution channel. The existing developer upload key signs the AAB submitted to Play; Google Play App Signing owns the signing identity of the APK actually delivered by Play.

Play availability never gates the existence of a GitHub Release unless a specific release claim explicitly depends on Play evidence.

### Switching channels

GitHub and Play may use different signing lineages while sharing the same package name. Android therefore may reject an in-place update from one channel to the other.

Harnex does not hide this invariant. Switching channel may require uninstall/reinstall and can remove app-private model/configuration state. No release note may imply seamless cross-channel update unless a future explicit signing-lineage migration has been implemented and qualified.

## Integration and release lines

- `dev` carries snapshot development and is the only normal base and target for feature work.
- `main` carries stable promotable history and receives ordinary changes only through a complete `dev -> main` promotion.
- Feature pull requests normally squash into `dev`; promotions use a merge commit to preserve the exact validated candidate.
- Tags, changelog release entries and distributed Android artifacts are created only from validated `main` commits.
- Emergency hotfixes are applied to `main` and then forward-ported to `dev`.

## GitHub Release candidate lifecycle

A GitHub Release uses two explicit phases.

### Prepare

The prepare phase:

1. checks out the exact current `main` commit;
2. requires the requested version to match `VERSION` and the versioned `CHANGELOG.md` section;
3. requires successful exact-main `Validate` and package automation;
4. builds the release APK from clean source without a development signing identity;
5. signs it with the protected GitHub Release key;
6. verifies the APK signature and records its signer digest;
7. generates release manifest, checksums and human release notes;
8. creates build-provenance attestation;
9. stores the immutable candidate as a bounded GitHub Actions artifact for release evidence.

No public tag or GitHub Release is created by prepare.

### Publish

The publish phase:

1. requires the exact prepare-run ID and reviewable REAL_ENVIRONMENT evidence identity;
2. downloads the prepared candidate rather than rebuilding it;
3. verifies version, source commit, APK SHA-256, APK size and signer digest against the manifest;
4. requires that source commit to remain the exact current `main` release source;
5. finalizes a release record containing the reviewed evidence reference;
6. creates the immutable `v<version>` tag and GitHub Release;
7. attaches the exact prepared APK and release metadata.

Existing releases are never overwritten. A retry may reuse an existing tag only when that tag resolves to the same exact source commit and no GitHub Release has yet been published.

## Play Internal phone-test identity

Every Google Play Internal Testing upload of `apps/local-llm-phone-test` uses one paired Android application identity:

- `versionCode` is strictly increasing and is resolved from current Play state as `max(uploaded versionCode) + 1`;
- `versionName` keeps the repository `major.minor` train and uses that exact Play `versionCode` as its patch component, for example `versionCode=34` -> `versionName=1.0.34` on the `1.0.x` train;
- protected CI must provide `PLAY_VERSION_CODE` and `PLAY_VERSION_NAME` together, and the build fails closed if the pair is incomplete or inconsistent;
- the canonical local signed-bundle helper increments both values together in `apps/local-llm-phone-test/version.properties`; it must not advance only one side of the pair;
- exact-candidate physical-E2E APK builds keep the checked-in identity unchanged because they are evidence artifacts, not Play deployments.

The source `versionName` patch is not the Play release counter. It provides the reviewed major/minor train; Play's monotonic version code owns the per-deploy patch identity. This lets repeated publication attempts or later deployments remain unique without mutating the validated source commit in CI.

## Release gate

A release requires:

- an exact `dev` candidate promoted to `main` through a protected pull request;
- complete non-scoped Android, native and packaging validation on the candidate;
- passing CI from a clean checkout;
- a versioned changelog entry matching `VERSION`;
- public API review;
- updated sample applications when relevant;
- model and device compatibility notes;
- benchmark comparison for runtime-critical changes;
- explicit cache/snapshot compatibility decision;
- checksums for distributed artifacts;
- release artifacts prepared and promoted immutably rather than rebuilt after evidence.

For a shared-runtime client/host distribution, the release gate additionally requires:

- production-shaped two-APK evidence for every claimed trust topology;
- for independently distributed consumers, distinct Host/consumer signer evidence, Consumer-before-Host reachability without Consumer reinstall, denial before explicit Harnex authorization and success after exact identity authorization;
- negative unknown/mismatched/replacement signer evidence without committed signing material;
- packaged release client-AAR consumer execution;
- cancellation, host-death/reconnect, memory and thermal evidence where material to the release claim;
- protocol compatibility fixtures and applicable package replacement/upgrade evidence;
- release notes binding Harnex version, host version, distribution channel, client SDK version, protocol identity, runtime/backend identity, Host/consumer signing identities and selected model evidence;
- a security review of the exported explicit-component Binder service, exact Binder caller identity policy, Harnex Control Plane authorization, bounded pre-authorization behavior and privacy boundary.

Same-key or Host-first-only emulator evidence does not satisfy an independently signed distribution claim. Physical distribution confirmation remains required when signing identity, real Android ARM64/JNI/GGUF behavior, memory, thermal or OEM behavior is material to the published claim.

## Development versions

Development builds on `dev` use snapshot semantics and are not releases. Harnex `0.5.0-rc.1` is the current first GitHub prerelease target; it may be published only after its exact `main` candidate, signing identity and release evidence gates close. The shared-runtime client keeps its own independently published version identity.

Neither Harnex `0.5.0-rc.1` nor the shared runtime may be described as universally production-ready until representative physical-device Qwen3.5 lifecycle, cancellation, memory, JNI-loading, thermal and applicable cross-process release evidence is complete.
