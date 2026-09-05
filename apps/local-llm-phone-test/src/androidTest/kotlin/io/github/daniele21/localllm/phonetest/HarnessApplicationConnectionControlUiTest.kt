package io.github.daniele21.localllm.phonetest

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.daniele21.localllm.ui.designsystem.HarnessTheme
import org.junit.Assert.assertEquals
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

    private fun pendingApplication() =
        HarnessApplicationSummary(
            applicationId = "redactguard",
            displayName = "RedactGuard",
            packageName = "io.github.daniele21.redactguard.debug",
            signerSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            status = HarnessApplicationStatus.PENDING,
            firstSeenAtEpochMs = 1L,
            lastSeenAtEpochMs = 1L,
            assignments = emptyList(),
        )
}
