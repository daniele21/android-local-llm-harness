# Evaluator semantics v1

Status: active
Document type: contract-reference
Owner: model-evaluation
Canonical scope: model-evaluation.evaluators.v1
Read when: authoring dataset evaluator specs, changing deterministic scoring behavior, or checking evaluator compatibility
Last reviewed: 2026-08-15

This document freezes the dataset-visible semantics of every deterministic evaluator version supported by model-evaluation v1. A behavior change that can alter an existing case score requires a new `EvaluatorVersion`; existing v1 behavior must not be silently reinterpreted.

## Registry contract

Evaluator lookup is fail-closed on the exact pair `(EvaluatorType, EvaluatorVersion)`. Specs with unknown types, unknown versions, missing required parameters, unknown parameter keys or disallowed parameter values fail preflight/validation rather than falling back to another scorer.

All v1 evaluator versions are `1`. No evaluator executes user-provided code or an arbitrary user-provided regular expression.

## EXACT_MATCH v1

Required parameters:

- `case`: `sensitive` or `insensitive`;
- `whitespace`: `exact`, `trim` or `collapse`.

Normalization order is whitespace policy first, then case policy. `collapse` trims and replaces every whitespace run with one ASCII space. Case-insensitive comparison uses locale-independent lowercase semantics.

Exact equality after normalization returns `CORRECT / 1.0`; every other valid comparison returns `INCORRECT / 0.0`.

## MULTIPLE_CHOICE v1

Required parameters:

- `labels`: comma-separated list of 2..32 labels;
- `case`: `sensitive` or `insensitive`.

Each label is an alphanumeric token of 1..16 characters and labels must remain unique after case normalization. The expected label must be one of the declared labels.

Generated text is searched for standalone declared labels, where alphanumeric/underscore characters cannot directly surround the label. Outcomes are:

- no declared label found -> `INVALID_OUTPUT / 0.0`;
- more than one distinct declared label found -> `AMBIGUOUS_OUTPUT / 0.0`;
- exactly the expected label -> `CORRECT / 1.0`;
- exactly one different allowed label -> `INCORRECT / 0.0`.

## NUMERIC_FINAL_ANSWER v1

Required parameter:

- `extraction`: `entire` or `last_number`.

Optional parameter:

- `absolute_tolerance`: canonical locale-independent decimal text, non-negative and at most `1000000000000`.

Numbers use optional sign, decimal point and optional scientific exponent. Decimal commas are not accepted. Expected answers must parse canonically or the case spec is invalid.

`entire` parses only the trimmed full output. `last_number` selects the final canonical number token found in the generated text. Missing/unparseable generated numeric output returns `INVALID_OUTPUT / 0.0`.

A parsed result is `CORRECT / 1.0` when `abs(actual - expected) <= absolute_tolerance`; otherwise it is `INCORRECT / 0.0`. Omitted tolerance means exact numeric equality.

## JSON_FIELDS v1

Required parameter:

- `required_fields`: comma-separated list of 1..32 unique field names matching `[A-Za-z_][A-Za-z0-9_.-]{0,63}`.

Expected and generated values must each be a JSON object. The expected object must contain every required field. Generated malformed/non-object JSON returns `INVALID_OUTPUT / 0.0`.

The parser is intentionally strict and bounded: input length <= 65,536 characters, string length <= 16,384, arrays <= 1,024 items, nesting depth <= 32, duplicate object keys rejected, leading-zero number forms rejected, and trailing non-whitespace content rejected.

Each required top-level field contributes equally. JSON equality is structural: object key sets must match recursively, array order is significant, strings/booleans/null compare exactly, and numeric values compare by decimal numeric value rather than textual representation.

Outcomes are:

- all required fields match -> `CORRECT / 1.0`;
- no required field matches -> `INCORRECT / 0.0`;
- otherwise -> `PARTIAL`, with score `matchedRequiredFields / requiredFields`.

## REGEX_FORMAT v1

Required parameters:

- `pattern_id`;
- `match_mode`: `full` or `find`.

Only repository-defined pattern IDs are accepted:

- `single_line_non_empty`;
- `integer`;
- `decimal`;
- `label_token`;
- `json_object_shape`.

`full` requires the selected repository pattern to match the entire output. `find` requires at least one matching substring. A match returns `CORRECT / 1.0`; otherwise `CONSTRAINT_VIOLATION / 0.0`.

`json_object_shape` is only a shape constraint and does not replace `JSON_FIELDS` structural parsing.

## INSTRUCTION_CONSTRAINTS v1

Required parameters:

- `constraints`: comma-separated list of 1..16 unique constraint IDs;
- `case`: `sensitive` or `insensitive`.

Supported constraints are:

- `non_empty`;
- `single_line`;
- `contains` + `contains_text`;
- `excludes` + `excludes_text`;
- `starts_with` + `starts_with_text`;
- `ends_with` + `ends_with_text`;
- `min_words` + `min_words` count;
- `max_words` + `max_words` count;
- `exact_lines` + `exact_lines` count;
- `format` + `format_pattern_id` and `format_match_mode`.

Text parameters must be non-empty. Count parameters are positive base-10 integers in `1..100000`. Parameters for a constraint that is not selected are rejected. The `format` constraint delegates only to `REGEX_FORMAT v1` repository-defined patterns.

Each selected constraint contributes equally:

- all pass -> `CORRECT / 1.0`;
- none pass -> `CONSTRAINT_VIOLATION / 0.0`;
- otherwise -> `PARTIAL`, with score `passedConstraints / declaredConstraints`.

Word count splits trimmed non-empty text on whitespace runs. Line count recognizes CRLF, CR and LF separators.

## Suite quality aggregation v1

Quality aggregation is separate from runtime, resources and reliability.

- `SCORED` contributes the evaluator's exact normalized score, including partial values;
- `INVALID_OUTPUT`, `TIMEOUT` and `RUNTIME_FAILURE` contribute quality `0`;
- `CANCELLED` is excluded from quality denominators;
- category quality is the arithmetic mean of quality-attempted cases in that category;
- categories with no quality-attempted cases are omitted from the suite aggregate;
- when every scored category declares a weight, the suite uses their weighted mean with weights renormalized over categories actually scored;
- when no scored category declares a weight, the suite uses the arithmetic mean of category scores;
- mixed weighted/unweighted scored categories fail closed.

## Compatibility freeze

Dataset packs must record the evaluator type, evaluator version and complete parameter map per case. `EvaluatorSetDigest` therefore changes whenever evaluator selection/version/parameters change.

The following changes require a new evaluator version rather than editing v1 in place:

- normalization or extraction rules;
- accepted label/number/JSON syntax;
- ambiguity handling;
- partial-score denominator or weighting;
- pattern definitions or constraint interpretation;
- failure/outcome mapping that changes score semantics;
- any parameter default or validation rule that changes which existing specs are accepted.

Pure refactors that provably preserve all v1 inputs, outcomes and scores may remain on v1 and must retain golden/adversarial test coverage.
