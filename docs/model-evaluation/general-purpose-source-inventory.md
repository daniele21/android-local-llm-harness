# General Purpose v1 public-source inventory

Status: active
Document type: feature-specification
Owner: model-evaluation
Canonical scope: model-evaluation.general-purpose.sources
Read when: curating, licensing or reproducing the public-derived portion of General Purpose v1
Last reviewed: 2026-08-15

This inventory freezes candidate upstream identities for EVAL-GP-01. It does **not** authorize redistribution or bundling. License, attribution and bundle-vs-download decisions remain EVAL-GP-02/EVAL-GP-03.

## Pinning rule

General Purpose v1 never consumes a floating `main`/`master` reference. Every curated source record must retain:

- upstream project/dataset identifier;
- exact immutable revision that resolves in the canonical upstream;
- exact config/path and split;
- upstream-native record ID where one exists;
- otherwise a deterministic source ID derived from the pinned revision, split and zero-based source ordinal;
- the original upstream identity separately from the Harness case ID.

A candidate pin is not accepted merely because it appears in a secondary benchmark copy. GP-01 verifies the canonical artifact/path at the exact upstream revision. The eventual 160 public-derived case list is frozen only by EVAL-GP-04 after source/license review and deterministic sampling are available.

## Candidate sources

| Harness category | Canonical upstream | Candidate revision | Config/path + split | Upstream record identity | Planned cases |
| --- | --- | --- | --- | --- | ---: |
| General knowledge/reasoning | `TIGER-Lab/MMLU-Pro` Hugging Face dataset | `24ac2da5bb7c7b42ea1a984c6b535e35a73d30b3` | `default`, `data/test-00000-of-00001.parquet`, `test` | native integer `question_id` | 60 |
| Instruction following | `google-research/google-research` | `26d8ccdab6fec61b5c83ad6327ea8bda9e580288` | `instruction_following_eval/data/input_data.jsonl` | native integer `key` | 40 |
| Mathematical reasoning | `openai/grade-school-math` | `b0bb162abedc65e1fdd8e93ed090fd7598ee68bc` | `grade_school_math/data/test.jsonl`, `test` | `gsm8k:<revision>:test:<zero-based-ordinal>` | 30 |
| Science/commonsense | `allenai/ai2_arc` Hugging Face dataset | `210d026faf9955653af8916fad021475a3f00453` | `ARC-Challenge`, `test-00000-of-00001.parquet`, `test` | native string `id` | 30 |

## Pin verification evidence

The two GitHub-backed inputs are pinned to commits returned by the canonical upstream file history rather than an inferred repository snapshot:

- IFEval `input_data.jsonl` resolves at `26d8ccdab6fec61b5c83ad6327ea8bda9e580288`, the latest commit touching that canonical file in the upstream history used by GP-01;
- GSM8K `test.jsonl` resolves at `b0bb162abedc65e1fdd8e93ed090fd7598ee68bc`, the upstream release commit that introduced the canonical test file.

The Hugging Face-backed MMLU-Pro and ARC Challenge pins resolve directly to the immutable dataset revisions and paths listed above. Pack construction must re-check path resolution and source-content digests before GP-04 freezes IDs.

## MMLU-Pro

The selected artifact is the upstream Hugging Face `TIGER-Lab/MMLU-Pro` dataset rather than a separately transformed benchmark copy. At the candidate revision the test parquet contains 12,032 records and exposes `question_id`, `question`, `options`, `answer`, `answer_index`, `cot_content`, `category` and `src`.

Source identity is `mmlu-pro:<question_id>`. EVAL-GP-04 must preserve the native question ID and source category while creating a separate Harness case ID.

The candidate revision is deliberately newer than the original 2024 release because the upstream test artifact was updated. Once GP-04 freezes IDs, changing to any later upstream revision requires a new Harness dataset version and a fresh provenance review.

## IFEval

The selected artifact is the Google Research `instruction_following_eval/data/input_data.jsonl` file. Records expose native `key`, `prompt`, `instruction_id_list` and constraint kwargs.

Source identity is `ifeval:<key>`. The upstream `key` is retained even when the Harness representation maps only the subset of instruction constraints supported by deterministic v1 evaluators.

GP-04 may select only source records whose instructions can be represented by the frozen Harness deterministic constraint vocabulary. Unsupported upstream instruction classes are excluded rather than approximated.

## GSM8K

The selected artifact is OpenAI's `grade_school_math/data/test.jsonl`. Each record contains `question` and `answer`; the answer stores reasoning followed by the final numeric answer introduced by `####`.

The canonical file does not expose a stable record ID. Because the upstream revision is immutable, GP-01 defines source identity as:

```text
gsm8k:<revision>:test:<zero-based-source-ordinal>
```

The ordinal refers to physical JSONL record order in the pinned file. GP-04 must additionally retain a source-content digest so accidental reordering/transformation is detectable before the Harness case IDs are frozen.

## ARC Challenge

The selected artifact is the AllenAI `ai2_arc` Hugging Face dataset, `ARC-Challenge` config. Records expose `id`, `question`, `choices.text`, `choices.label` and `answerKey`.

Source identity is `arc-challenge:<id>`. The candidate pin includes the parquet-converted Challenge split and is immutable; the Harness must not rely on Hugging Face `main` during pack construction.

## Provenance record required at curation time

Every public-derived Harness case must carry source metadata sufficient to reconstruct its origin before content is packaged:

```text
sourceFamily
upstreamRepositoryOrDataset
upstreamRevision
upstreamConfigOrPath
upstreamSplit
upstreamRecordId
sourceContentDigest
```

This provenance belongs to dataset/source metadata, not ordinary inference telemetry.

## Deferred to GP-02 / GP-03

This inventory intentionally does not decide:

- whether source records may be redistributed inside the repository or APK;
- required notices/attribution text;
- whether a source must be downloaded separately rather than bundled;
- compatibility between upstream dataset/content licenses and this repository's distribution model;
- whether any source must be blocked entirely.

Those questions are explicit gates. GP-04 cannot freeze the 160 public-derived source IDs until GP-03 has an accepted treatment for every source family.
