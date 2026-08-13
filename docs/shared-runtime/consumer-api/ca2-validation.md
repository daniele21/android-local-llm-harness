# CA-2 validation record

Status: active
Document type: validation-record
Owner: shared-runtime-consumer-api
Canonical scope: shared-runtime.consumer-api.ca2-validation
Read when: validating or reviewing the CA-2 public consumer facade
Last reviewed: 2026-08-13

## Exit gate

CA-2 must prove the public lifecycle `discover -> prepare -> session -> generate -> close` using consumer-only types.

Focused tests must also prove that stale selections and invalid input/output fail before legacy delegation, and that the public surface exposes no caller-controlled application identity, model/artifact identity or raw generation tuning.

## Evidence

The implementation evidence lives in `ConsumerLocalLlmFacadeTest`. Repository-wide CI remains the final acceptance gate for CA-2.
