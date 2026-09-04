package io.github.daniele21.localllm.transport.binder.contract

import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.daniele21.localllm.contracts.ConsumerPreparationAction
import io.github.daniele21.localllm.contracts.ConsumerRuntimePhase
import kotlinx.parcelize.parcelableCreator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParcelableRoundTripTest {
    @Test
    fun protocolInfoRoundTripsThroughParcel() {
        val value =
            ProtocolInfoParcel(
                protocolMajor = BinderProtocolV1.MAJOR,
                protocolMinor = BinderProtocolV1.MINOR,
                minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
                supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
                hostBuildId = "host-fixture",
            )

        assertEquals(value, roundTrip(value, parcelableCreator<ProtocolInfoParcel>()))
    }

    @Test
    fun generationRequestRoundTripsThroughParcel() {
        val value =
            GenerationRequestParcel(
                clientToken = ClientTokenParcel("token-fixture"),
                externalRequestId = "request-fixture",
                externalSessionId = "session-fixture",
                useCaseId = "summarize",
                input =
                GenerationInputParcel(
                    typeTag = WireTags.INPUT_MESSAGES,
                    text = null,
                    messages =
                    listOf(
                        ConversationMessageParcel(WireTags.ROLE_USER, "fixture request"),
                        ConversationMessageParcel(WireTags.ROLE_ASSISTANT, "fixture response"),
                    ),
                ),
                overrides =
                GenerationOverridesParcel(
                    presetId = "balanced",
                    presetVersion = 1,
                    maxOutputTokens = 128,
                    temperature = 0.2f,
                    topP = 0.9f,
                    topK = 20,
                    seedPolicyTag = WireTags.SEED_FIXED,
                    seedValue = 42,
                    repeatPenalty = 1.05f,
                    repeatLastN = 64,
                    thinkingModeTag = WireTags.THINKING_DISABLED,
                    minP = 0.05f,
                    presencePenalty = 0.1f,
                ),
                outputConstraint = OutputConstraintParcel(WireTags.CONSTRAINT_JSON, null),
            )

        assertEquals(value, roundTrip(value, parcelableCreator<GenerationRequestParcel>()))
    }

    @Test
    fun consumerGenerationV2WithTaskDefinitionsRoundTripsThroughParcel() {
        val baseRequest =
            ConsumerRequestParcel(
                clientToken = ClientTokenParcel("consumer-token"),
                operationId = "operation-1",
                externalSessionId = "session-1",
                externalRequestId = "request-1",
                input = ConsumerGenerationInputParcel(WireTags.INPUT_TEXT, "document text", emptyList()),
                outputConstraint = ConsumerOutputConstraintParcel(WireTags.CONSTRAINT_JSON, null),
            )
        val value =
            ConsumerGenerationRequestV2Parcel(
                request = baseRequest,
                taskDefinitions =
                listOf(
                    TaskDefinitionParcel("email", "An email address", "ada@example.test"),
                    TaskDefinitionParcel("health-condition", "A diagnosed health condition"),
                ),
            )

        assertEquals(value, roundTrip(value, parcelableCreator<ConsumerGenerationRequestV2Parcel>()))
    }

    @Test
    fun consumerRuntimeReadinessRoundTripsThroughParcel() {
        val value =
            ConsumerRuntimeReadinessResultParcel(
                operationId = "readiness-operation",
                activationId = "activation-opaque",
                phaseTag = ConsumerRuntimePhase.PREPARING.name,
                preparationActionTag = ConsumerPreparationAction.SWITCHING.name,
                retryable = false,
            )

        assertEquals(value, roundTrip(value, parcelableCreator<ConsumerRuntimeReadinessResultParcel>()))
    }

    @Test
    fun generationEventRoundTripsThroughParcel() {
        val value =
            GenerationEventParcel(
                externalRequestId = "request-fixture",
                sequence = 4,
                eventTag = WireTags.EVENT_TEXT_DELTA,
                deltaText = "bounded fixture delta",
                generatedTokens = 3,
                contentTypeTag = WireTags.CONTENT_ANSWER,
            )

        assertEquals(value, roundTrip(value, parcelableCreator<GenerationEventParcel>()))
    }

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        return try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
