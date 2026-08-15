# Evaluation comparison

`evaluation/comparison` owns compatibility assessment between evaluation runs. It is domain-only and depends on frozen evaluation identities; it does not query storage or render UI.

Quality compatibility checks dataset digest, ordered sample-set digest, evaluator-set digest and semantic-execution fingerprint. The selected model is intentionally not part of quality compatibility, because the capability exists to compare different supported models on identical work.

Runtime compatibility additionally checks device class, Android API, ABI, backend revision, Harness build, runtime tuning profile, load policy and warm-up policy. Any quality mismatch is also surfaced as `QUALITY_INCOMPATIBLE` for runtime comparison.

P-08 stops at compatibility. Numeric/category/runtime delta calculation remains P-09.
