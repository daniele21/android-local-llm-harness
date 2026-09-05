package io.github.daniele21.localllm.phonetest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.daniele21.localllm.audit.InferenceAuditFailureCode
import io.github.daniele21.localllm.audit.InferenceAuditResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

internal data class HarnessInferenceActivityState(
    val loading: Boolean = true,
    val items: List<InferenceActivityListItem> = emptyList(),
    val listErrorCode: InferenceAuditFailureCode? = null,
    val selectedRequestId: String? = null,
    val detailLoading: Boolean = false,
    val detail: InferenceActivityDetail? = null,
    val detailErrorCode: InferenceAuditFailureCode? = null,
    val mutationInProgress: Boolean = false,
    val feedback: String? = null,
)

internal class HarnessInferenceActivityViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(HarnessInferenceActivityState())
    private val generation = AtomicLong(0)
    private var source: HarnessInferenceActivitySource? = null

    val state: StateFlow<HarnessInferenceActivityState> = mutableState.asStateFlow()

    fun attach(source: HarnessInferenceActivitySource) {
        if (this.source === source) return
        this.source = source
        refresh()
    }

    fun detach(source: HarnessInferenceActivitySource) {
        if (this.source !== source) return
        this.source = null
        generation.incrementAndGet()
    }

    fun refresh() {
        val attached = source
        if (attached == null) {
            mutableState.value = mutableState.value.copy(
                loading = false,
                listErrorCode = InferenceAuditFailureCode.UNAVAILABLE,
            )
            return
        }
        val token = generation.incrementAndGet()
        mutableState.value = mutableState.value.copy(loading = true, listErrorCode = null, feedback = null)
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = runCatching(attached::snapshot).getOrElse {
                InferenceActivityUiState(errorCode = InferenceAuditFailureCode.STORAGE_FAILURE)
            }
            if (!isCurrent(token, attached)) return@launch
            mutableState.value = mutableState.value.copy(
                loading = false,
                items = snapshot.items,
                listErrorCode = snapshot.errorCode,
            )
        }
    }

    fun openDetail(requestId: String) {
        val attached = source ?: return
        val token = generation.incrementAndGet()
        mutableState.value = mutableState.value.copy(
            selectedRequestId = requestId,
            detailLoading = true,
            detail = null,
            detailErrorCode = null,
            feedback = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { attached.detail(requestId) }.getOrElse {
                InferenceActivityDetailResult.Unavailable(InferenceAuditFailureCode.STORAGE_FAILURE)
            }
            if (!isCurrent(token, attached)) return@launch
            mutableState.value = when (result) {
                is InferenceActivityDetailResult.Available -> mutableState.value.copy(
                    detailLoading = false,
                    detail = result.detail,
                    detailErrorCode = null,
                )

                is InferenceActivityDetailResult.Unavailable -> mutableState.value.copy(
                    detailLoading = false,
                    detail = null,
                    detailErrorCode = result.errorCode ?: InferenceAuditFailureCode.NOT_FOUND,
                )
            }
        }
    }

    fun clearTerminalHistory() {
        val attached = source ?: return
        val token = generation.incrementAndGet()
        mutableState.value = mutableState.value.copy(mutationInProgress = true, feedback = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching(attached::clearTerminalHistory).getOrElse {
                InferenceAuditResult.Failure(InferenceAuditFailureCode.STORAGE_FAILURE)
            }
            val snapshot = if (result is InferenceAuditResult.Success) {
                runCatching(attached::snapshot).getOrElse {
                    InferenceActivityUiState(errorCode = InferenceAuditFailureCode.STORAGE_FAILURE)
                }
            } else {
                null
            }
            if (!isCurrent(token, attached)) return@launch
            mutableState.value = when (result) {
                is InferenceAuditResult.Success -> mutableState.value.copy(
                    loading = false,
                    items = snapshot?.items.orEmpty(),
                    listErrorCode = snapshot?.errorCode,
                    mutationInProgress = false,
                    selectedRequestId = null,
                    detail = null,
                    detailErrorCode = null,
                    feedback = "Cleared ${result.value} completed activity records.",
                )

                is InferenceAuditResult.Failure -> mutableState.value.copy(
                    mutationInProgress = false,
                    feedback = "Activity history could not be cleared (${result.code.name}).",
                )
            }
        }
    }

    fun clearFeedback() {
        mutableState.value = mutableState.value.copy(feedback = null)
    }

    private fun isCurrent(token: Long, attached: HarnessInferenceActivitySource): Boolean =
        generation.get() == token && source === attached

    override fun onCleared() {
        generation.incrementAndGet()
        source = null
        super.onCleared()
    }
}
