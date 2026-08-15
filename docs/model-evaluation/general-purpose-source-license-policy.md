# General Purpose v1 source license and distribution policy

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.general-purpose.licensing
Read when: packaging, downloading, attributing or curating public-derived General Purpose v1 cases
Last reviewed: 2026-08-15

## Purpose

This document records the EVAL-GP-02 license/attribution review and the EVAL-GP-03 project distribution treatment for every public source pinned by [`general-purpose-source-inventory.md`](general-purpose-source-inventory.md).

The policy is deliberately fail-closed. A source may be used by GP-04 only when it has one of these explicit treatments:

- `BUNDLE_APPROVED` — the selected source records may be redistributed as part of the Harness dataset pack when the required notices/attribution metadata are shipped with the pack;
- `DOWNLOAD_ONLY` — Harness may publish source identity, selection metadata and retrieval instructions, but does not redistribute the upstream records inside the repository/APK; the device obtains the pinned upstream artifact and builds the local pack after download;
- `BLOCKED` — the source cannot contribute cases to General Purpose v1 until a new explicit review changes the treatment.

This is a project packaging policy based on the upstream license declarations at the pinned sources. It is not an independent audit of every underlying fact/question provenance and must not be represented as legal certification.

## Decision table

| Source | Pinned revision | Upstream license evidence | Treatment | Required distribution behavior |
| --- | --- | --- | --- | --- |
| MMLU-Pro | `24ac2da5bb7c7b42ea1a984c6b535e35a73d30b3` | Hugging Face dataset metadata declares `mit` | `BUNDLE_APPROVED` | retain upstream dataset identity, MIT notice/copyright where supplied, citation and Harness transformation notice |
| IFEval | `26d8ccdab6fec61b5c83ad6327ea8bda9e580288` | Google Research root policy declares repository datasets CC BY 4.0 | `BUNDLE_APPROVED` | credit Google Research/IFEval, identify CC BY 4.0, provide license reference and indicate Harness transformations/subsetting |
| GSM8K | `b0bb162abedc65e1fdd8e93ed090fd7598ee68bc` | canonical repository `LICENSE` is MIT, copyright 2021 OpenAI | `BUNDLE_APPROVED` | retain MIT copyright/permission notice, source identity, citation and Harness transformation notice |
| ARC Challenge | `210d026faf9955653af8916fad021475a3f00453` | pinned Hugging Face dataset metadata declares CC BY-SA 4.0 | `DOWNLOAD_ONLY` | do not ship transformed ARC records in repository/APK; retain source/license attribution and build the selected local representation only after the pinned artifact is downloaded |

## Why ARC is download-only in v1

CC BY-SA 4.0 permits sharing and adaptation, including commercial use, but adapted material distributed by the project carries ShareAlike obligations. General Purpose v1 does not copy ARC records byte-for-byte: it selects a subset and transforms each selected question into the Harness canonical evaluation representation.

The v1 project therefore avoids silently imposing a dataset-specific ShareAlike packaging policy on a mixed 200-case bundle. ARC is `DOWNLOAD_ONLY` until one of these explicit project decisions occurs:

1. Harness adopts and documents a separate CC BY-SA 4.0 licensing boundary for the redistributed ARC-derived portion; or
2. ARC is replaced by a source with a distribution treatment compatible with the desired fully bundled pack.

`DOWNLOAD_ONLY` does not mean ARC scores are less valid. It changes delivery, not evaluator semantics or comparison identity. The installed local pack still freezes the exact upstream revision, selected case IDs and final dataset digest.

## Attribution payload

Every public-derived installed pack must retain a machine-readable source attribution record equivalent to:

```text
sourceFamily
upstreamRepositoryOrDataset
upstreamRevision
upstreamConfigOrPath
upstreamSplit
licenseId
licenseNoticeOrAttribution
transformationNotice
distributionTreatment
```

GP-10 owns the final pack metadata shape. These fields are source/dataset metadata and must not be copied into ordinary inference telemetry.

### MMLU-Pro

Minimum notice content:

- source name `MMLU-Pro`;
- canonical dataset identity `TIGER-Lab/MMLU-Pro`;
- pinned revision;
- MIT license identifier and required upstream notice when available;
- benchmark citation;
- statement that Harness selects and transforms a subset and that Harness subset scores are not official full-benchmark scores.

### IFEval

Minimum notice content:

- Google Research `instruction_following_eval` / IFEval source identity;
- pinned revision;
- CC BY 4.0 attribution;
- license reference;
- indication that the Harness subset was selected and transformed to the deterministic constraint vocabulary;
- no implication that Google endorses the Harness subset or its scores.

### GSM8K

Minimum notice content:

- `openai/grade-school-math` / GSM8K source identity;
- pinned release revision;
- MIT copyright and permission notice from the canonical repository;
- benchmark citation;
- statement that Harness selects a subset and extracts deterministic final numeric answers for its evaluator representation.

### ARC Challenge

Minimum local-install attribution:

- `allenai/ai2_arc`, `ARC-Challenge`;
- pinned revision;
- CC BY-SA 4.0 identifier and license reference;
- benchmark citation;
- statement that the device created a local Harness representation from the downloaded pinned source;
- Harness subset/device score labeling.

## Bundle and download invariants

### `BUNDLE_APPROVED`

A bundled source component must satisfy all of the following before GP-07 freezes the dataset digest:

- upstream pin resolves to the canonical source/path;
- selected source IDs are frozen by GP-04;
- required attribution/license notice is included in pack metadata and distribution notices;
- transformations are identified;
- the repository does not relabel a Harness subset result as an official upstream benchmark result.

### `DOWNLOAD_ONLY`

A download-only component must satisfy:

- the APK/repository does not contain the selected upstream record content;
- download uses the exact immutable revision, never floating `main`;
- downloaded bytes/content are verified before local transformation;
- GP-04 selection is performed against the pinned source identity;
- local transformed cases enter app-private dataset storage;
- the final installed dataset identity includes the locally materialized content digest;
- failure to resolve/verify the pinned source fails closed and does not expose a partially installed General Purpose pack.

### `BLOCKED`

A blocked source contributes zero cases. The category plan must be revised explicitly rather than silently backfilling records from another source family.

## GP-03 acceptance decision

With the treatments above, no current source is unconditionally blocked:

```text
MMLU-Pro     -> BUNDLE_APPROVED
IFEval       -> BUNDLE_APPROVED
GSM8K        -> BUNDLE_APPROVED
ARC Challenge-> DOWNLOAD_ONLY
```

GP-04 may therefore define deterministic source-case selection rules only after this policy and the corrected GP-01 pins pass repository documentation/current-state gates. ARC selection must remain content-free in the repository until the download/install implementation exists.

## Change control

Changing any of these requires a new review before packaging:

- upstream revision;
- upstream license declaration;
- source path/config/split;
- distribution treatment;
- transformation that materially changes the licensing boundary;
- repository/app distribution model in a way that affects required notices or ShareAlike handling.

A later decision to bundle ARC is not a documentation-only wording change: GP-03 must be reopened and the pack distribution boundary must explicitly satisfy the accepted CC BY-SA 4.0 treatment.
