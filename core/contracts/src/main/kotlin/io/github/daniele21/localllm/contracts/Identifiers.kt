package io.github.daniele21.localllm.contracts

@JvmInline
value class ApplicationId(val value: String)

@JvmInline
value class UseCaseId(val value: String)

@JvmInline
value class SessionId(val value: String)

@JvmInline
value class RequestId(val value: String)

@JvmInline
value class ModelDigest(val sha256: String)
