package io.github.daniele21.localllm.phonetest

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.daniele21.localllm.ui.designsystem.HarnessTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class HarnessApplicationConnectionControlUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pendingApplicationCanBeExplicitlyAuthorizedFromSwitch() {
        var requested: Boolean? = null
        composeRule.setContent {
            HarnessTheme(darkTheme = false) {
                HarnessConnectionControlCard(
                    application = pendingApplication(),
                    saving = false,
                    onConnectionEnabledChanged = { requested = it },
                )
            }
        }

        composeRule
            .onNodeWithTag("application-connection-enabled")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(true, requested)
        }
    }

    @Test
    fun createConnectionButtonSubmitsExactVisibleForm() {
        var submission: ConnectionSubmission? = null
        composeRule.setContent {
            HarnessTheme(darkTheme = false) {
                HarnessCreateApplicationConnectionScreen(
                    options = listOf(genericTextOption()),
                    mutationState = HarnessApplicationsMutationState.Idle,
                    onCreate = { applicationId, displayName, packageName, signer, useCaseId, presetId, presetRevision ->
                        submission =
                            ConnectionSubmission(
                                applicationId = applicationId,
                                displayName = displayName,
                                packageName = packageName,
                                signerSha256 = signer,
                                useCaseId = useCaseId,
                                presetId = presetId,
                                presetRevision = presetRevision,
                            )
                    },
                    onReload = {},
                    onClearFeedback = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithTag("connection-display-name").performTextInput("Hello Harnex")
        composeRule.onNodeWithTag("connection-application-id").performTextInput("hello-harnex")
        composeRule.onNodeWithTag("connection-package-name").performTextInput("io.github.daniele21.harnex.hello")
        composeRule.onNodeWithTag("connection-signer-sha256").performTextInput("a".repeat(64))
        composeRule.onNodeWithTag("connection-create").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertNotNull(submission)
            val submitted = requireNotNull(submission)
            assertEquals("hello-harnex", submitted.applicationId)
            assertEquals("Hello Harnex", submitted.displayName)
            assertEquals("io.github.daniele21.harnex.hello", submitted.packageName)
            assertEquals("a".repeat(64), submitted.signerSha256)
            assertEquals("generic-text-generation", submitted.useCaseId)
            assertEquals("qwen35-text-quality", submitted.presetId)
            assertEquals(1, submitted.presetRevision)
        }
    }

    @Test
    fun createConnectionAlwaysShowsMutationOutcomeNearAction() {
        val mutationState = mutableStateOf<HarnessApplicationsMutationState>(HarnessApplicationsMutationState.Saving)
        composeRule.setContent {
            HarnessTheme(darkTheme = false) {
                HarnessCreateApplicationConnectionScreen(
                    options = listOf(genericTextOption()),
                    mutationState = mutationState.value,
                    onCreate = { _, _, _, _, _, _, _ -> },
                    onReload = {},
                    onClearFeedback = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithTag("connection-create").assertTextEquals("Creating connection…")
        composeRule.onNodeWithText("Saving").assertExists()

        composeRule.runOnIdle {
            mutationState.value = HarnessApplicationsMutationState.Saved(
                "Application connection created and enabled.",
            )
        }
        composeRule.onNodeWithText("Connection ready").assertExists()
        composeRule.onNodeWithText("Application connection created and enabled.").assertExists()

        composeRule.runOnIdle {
            mutationState.value = HarnessApplicationsMutationState.Failed(
                "Package name is already connected",
            )
        }
        composeRule.onNodeWithText("Connection not created").assertExists()
        composeRule.onNodeWithText("Package name is already connected").assertExists()
    }

    private fun pendingApplication() = HarnessApplicationSummary(
        applicationId = "redactguard",
        displayName = "RedactGuard",
        packageName = "io.github.daniele21.redactguard.debug",
        signerSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        status = HarnessApplicationStatus.PENDING,
        firstSeenAtEpochMs = 1L,
        lastSeenAtEpochMs = 1L,
        assignments = emptyList(),
    )

    private fun genericTextOption() = HarnessConnectionUseCaseOption(
        useCaseId = "generic-text-generation",
        useCaseRevision = 1,
        displayName = "Generic text generation",
        description = "Run bounded stateless local text generation",
        presets =
            listOf(
                HarnessConnectionPresetOption(
                    presetId = "qwen35-text-quality",
                    revision = 1,
                    displayName = "Quality",
                    description = "General-purpose non-thinking local text generation",
                ),
            ),
    )
}
