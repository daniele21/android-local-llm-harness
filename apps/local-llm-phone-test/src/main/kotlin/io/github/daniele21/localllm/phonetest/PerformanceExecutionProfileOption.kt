package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.evaluation.EvaluationExecutionProfileRef

internal data class PerformanceExecutionProfileOption(
    val ref: EvaluationExecutionProfileRef,
    val label: String,
    val description: String,
    val compatible: Boolean = true,
    val incompatibilityReason: String? = null,
) {
    init {
        require(label.isNotBlank()) { "Performance execution-profile label must not be blank" }
        require(description.isNotBlank()) { "Performance execution-profile description must not be blank" }
        require(compatible || !incompatibilityReason.isNullOrBlank()) {
            "Incompatible Performance execution profile requires a reason"
        }
    }
}
