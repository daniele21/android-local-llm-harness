# Historical connected-phone migration slices

Status: historical
Document type: historical-plan
Owner: apps/local-llm-phone-test
Last reviewed: 2026-08-06
Active replacement: [`../../features/phone-app-architecture.md`](../../features/phone-app-architecture.md)

This record replaces the temporary implementation-slice documents for the initial ViewModel/UDF foundation and typed detail navigation.

The completed slices established:

- immutable `HarnessUiState`, typed events, a pure reducer and `HarnessViewModel`;
- ViewModel/effect boundaries for Playground and Models while retaining Android/native resources in the Activity lifecycle;
- top-level Navigation Compose destinations and detail routes for Settings, request timelines and model details;
- opaque URL-safe navigation identifiers;
- deterministic Back behavior without persisting prompts or generated output;
- removal of duplicate Activity-owned Playground and model state.

The original documents remain available in Git history at:

- `docs/harness-viewmodel-udf-foundation.md`;
- `docs/harness-detail-navigation.md`.

They described next steps that have since been partially completed and must not be used as active plans. Remaining architecture work is documented in the active phone-application specification and current-state ledger.
