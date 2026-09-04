package io.github.daniele21.localllm.integration.servicehost

internal fun <T> LedgerResult<T>.successOrNull(): T? = (this as? LedgerResult.Success)?.value

internal fun LedgerResult<*>.failureOrNull(): LedgerFailure? = (this as? LedgerResult.Failure)?.reason
