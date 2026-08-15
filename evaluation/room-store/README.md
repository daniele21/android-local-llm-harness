# Evaluation Room schema

`evaluation/room-store` owns Room-specific persistence shapes for evaluation history. It is intentionally separate from `observability/room-store`; deleting or migrating evaluation history must not couple to telemetry retention or telemetry schema ownership.

P-03 defines the normalized privacy-safe schema only. DAO/repository/database wiring remains P-04.

The schema stores:

- run configuration and reproducible identity fields;
- ordered sampled case IDs as ordinal rows;
- aggregate category/reliability state;
- per-case typed outcomes, request correlation and privacy-safe metrics;
- evaluator parameters as normalized key/value rows;
- typed bounded failures.

It does not contain prompt text, message content, expected answers or generated answers. Dataset content remains in dataset storage; ordinary telemetry remains in its existing repository.
