package io.github.daniele21.localllm.console.ombra

import io.github.daniele21.localllm.console.pii.PiiDefinitionDraft
import io.github.daniele21.localllm.console.pii.PiiDefinitionValidation
import io.github.daniele21.localllm.console.pii.PiiTypeId
import io.github.daniele21.localllm.console.redaction.ReviewDecisionState

internal fun OmbraProductViewModel.toggleDefinition(id: PiiTypeId, selected: Boolean) = definitionActions.toggle(id, selected)

internal fun OmbraProductViewModel.addCustomDefinition(draft: PiiDefinitionDraft): PiiDefinitionValidation? =
    definitionActions.addCustom(draft)

internal fun OmbraProductViewModel.prepareReview() = reviewActions.prepare()

internal fun OmbraProductViewModel.moveReview(delta: Int) = reviewActions.move(delta)

internal fun OmbraProductViewModel.toggleReveal() = reviewActions.toggleReveal()

internal fun OmbraProductViewModel.setReviewDecision(decision: ReviewDecisionState): Boolean = reviewActions.decide(decision)

internal fun OmbraProductViewModel.suggestedExportName(): String = reviewActions.suggestedExportName()
