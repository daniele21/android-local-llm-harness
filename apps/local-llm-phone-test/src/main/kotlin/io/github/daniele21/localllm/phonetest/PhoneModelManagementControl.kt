package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.contracts.ModelDigest
import io.github.daniele21.localllm.store.ModelStore

internal enum class PhoneModelManagementOperation {
    VERIFY,
    REMOVE,
}

internal data class PhoneModelManagementOutcome(
    val operation: PhoneModelManagementOperation,
    val digest: ModelDigest,
    val success: Boolean,
    val detail: String,
    val sourceError: String? = null,
)

internal interface PhoneModelManagementGateway {
    fun verify(digest: ModelDigest): PhoneModelManagementOutcome

    fun remove(digest: ModelDigest): PhoneModelManagementOutcome
}

@Suppress("TooGenericExceptionCaught")
internal class ModelStorePhoneModelManagementControl(
    private val modelStore: ModelStore,
    private val protectedModelDigest: () -> ModelDigest?,
    private val removeMetadata: (ModelDigest) -> Boolean,
) : PhoneModelManagementGateway {
    override fun verify(digest: ModelDigest): PhoneModelManagementOutcome = try {
        val result = modelStore.verify(digest)
        PhoneModelManagementOutcome(
  operation = PhoneModelManagementOperation.VERIFY,
  digest = digest,
  success = result.valid,
  detail = if (result.valid) {
      "Model integrity verified"
  } else {
      "Model integrity check failed"
  },
        )
    } catch (_: RuntimeException) {
        unavailable(PhoneModelManagementOperation.VERIFY, digest)
    }

    override fun remove(digest: ModelDigest): PhoneModelManagementOutcome {
        if (protectedModelDigest() == digest) {
  return PhoneModelManagementOutcome(
      operation = PhoneModelManagementOperation.REMOVE,
      digest = digest,
      success = false,
      detail = "Selected or loaded model cannot be removed",
  )
        }
        return try {
  val removed = modelStore.remove(digest)
  if (removed) removeMetadata(digest)
  PhoneModelManagementOutcome(
      operation = PhoneModelManagementOperation.REMOVE,
      digest = digest,
      success = removed,
      detail = if (removed) "Model removed" else "Model not found",
  )
        } catch (_: RuntimeException) {
  unavailable(PhoneModelManagementOperation.REMOVE, digest)
        }
    }

    private fun unavailable(
        operation: PhoneModelManagementOperation,
        digest: ModelDigest,
    ): PhoneModelManagementOutcome = PhoneModelManagementOutcome(
        operation = operation,
        digest = digest,
        success = false,
        detail = MODEL_MANAGEMENT_ERROR,
        sourceError = MODEL_MANAGEMENT_ERROR,
    )

    private companion object {
        const val MODEL_MANAGEMENT_ERROR = "Model management unavailable"
    }
}
