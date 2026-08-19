package io.github.daniele21.localllm.models

import io.github.daniele21.localllm.contracts.ApplicationId
import io.github.daniele21.localllm.contracts.SessionKind
import io.github.daniele21.localllm.contracts.UseCaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignedUseCaseDiscoveryTest {
    @Test
    fun `discovery exposes only current active assignments for requested app`() {
        val state = state()
        val result = AssignedUseCaseDiscovery(InMemoryHostControlPlaneStore(state)).discover(APP_A)
            as AssignedUseCaseDiscoveryResult.Success

        assertEquals(2, result.assignments.size)
        assertEquals(USE_CASE_DEFAULT, result.assignments.first().useCaseId)
        assertTrue(result.assignments.first().isDefault)
        assertEquals(2, result.assignments.first().bindingRevision)
        assertEquals(2, result.assignments.first().useCaseRevision)
        assertEquals(USE_CASE_SECONDARY, result.assignments.last().useCaseId)
        assertFalse(result.assignments.last().isDefault)
        assertFalse(result.assignments.any { it.useCaseId == USE_CASE_DISABLED_BINDING })
        assertFalse(result.assignments.any { it.useCaseId == USE_CASE_DRAFT })
    }

    @Test
    fun `discovery never enumerates another applications assignments`() {
        val result = AssignedUseCaseDiscovery(InMemoryHostControlPlaneStore(state())).discover(APP_A)
            as AssignedUseCaseDiscoveryResult.Success

        assertFalse(result.assignments.any { it.useCaseId == USE_CASE_OTHER_APP })
    }

    @Test
    fun `disabled latest binding does not fall back to older enabled revision`() {
        val result = AssignedUseCaseDiscovery(InMemoryHostControlPlaneStore(state())).discover(APP_A)
            as AssignedUseCaseDiscoveryResult.Success

        assertFalse(result.assignments.any { it.useCaseId == USE_CASE_DISABLED_BINDING })
    }

    @Test
    fun `unauthorized application fails discovery`() {
        val source = state().copy(
            applications = state().applications.map { application ->
                if (application.applicationId == APP_A) {
                    application.copy(state = ApplicationRegistrationState.DISABLED)
                } else {
                    application
                }
            },
        )

        val result = AssignedUseCaseDiscovery(InMemoryHostControlPlaneStore(source)).discover(APP_A)
            as AssignedUseCaseDiscoveryResult.Failure

        assertEquals(AssignedUseCaseDiscoveryFailure.APPLICATION_NOT_AUTHORIZED, result.reason)
    }

    @Test
    fun `state rejects two current default assignments for one application`() {
        val base = state()
        val invalid = runCatching {
            HostControlPlaneState(
                applications = base.applications,
                useCases = base.useCases,
                bindings = listOf(
                    binding(APP_A, USE_CASE_DEFAULT, "default", revision = 1, isDefault = true),
                    binding(APP_A, USE_CASE_SECONDARY, "secondary", revision = 1, isDefault = true),
                ),
            )
        }

        assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
    }

    private fun state(): HostControlPlaneState = HostControlPlaneState(
        applications = listOf(
            application(APP_A, "App A"),
            application(APP_B, "App B"),
        ),
        useCases = listOf(
            useCase(USE_CASE_DEFAULT, revision = 1),
            useCase(USE_CASE_DEFAULT, revision = 2),
            useCase(USE_CASE_SECONDARY),
            useCase(USE_CASE_DISABLED_BINDING),
            useCase(USE_CASE_DRAFT, state = UseCaseDefinitionState.DRAFT),
            useCase(USE_CASE_OTHER_APP),
        ),
        bindings = listOf(
            binding(APP_A, USE_CASE_DEFAULT, "default", revision = 1, isDefault = true),
            binding(APP_A, USE_CASE_DEFAULT, "default", revision = 2, isDefault = true),
            binding(APP_A, USE_CASE_SECONDARY, "secondary"),
            binding(APP_A, USE_CASE_DISABLED_BINDING, "disabled", revision = 1),
            binding(APP_A, USE_CASE_DISABLED_BINDING, "disabled", revision = 2, enabled = false),
            binding(APP_A, USE_CASE_DRAFT, "draft"),
            binding(APP_B, USE_CASE_OTHER_APP, "other", isDefault = true),
        ),
    )

    private fun application(applicationId: ApplicationId, name: String): RegisteredApplication = RegisteredApplication(
        applicationId = applicationId,
        packageName = "io.github.${applicationId.value}",
        signerSha256 = "a".repeat(64),
        displayName = name,
        state = ApplicationRegistrationState.AUTHORIZED,
        firstSeenAtEpochMs = 1,
        lastSeenAtEpochMs = 2,
    )

    private fun useCase(
        useCaseId: UseCaseId,
        revision: Int = 1,
        state: UseCaseDefinitionState = UseCaseDefinitionState.ACTIVE,
    ): UseCaseDefinition {
        return UseCaseDefinition(
            useCaseId = useCaseId,
            displayName = useCaseId.value,
            description = "Description for ${useCaseId.value}",
            requirements = UseCaseRequirements(
                outputMode = OutputMode.TEXT,
                sessionKind = SessionKind.STATELESS,
                reasoningSupported = false,
                minimumContextTokens = 1_024,
            ),
            state = state,
            revision = revision,
        )
    }

    private fun binding(
        applicationId: ApplicationId,
        useCaseId: UseCaseId,
        bindingId: String,
        revision: Int = 1,
        enabled: Boolean = true,
        isDefault: Boolean = false,
    ): ApplicationUseCaseBinding {
        return ApplicationUseCaseBinding(
            bindingId = bindingId,
            applicationId = applicationId,
            useCaseId = useCaseId,
            revision = revision,
            enabled = enabled,
            isDefault = isDefault,
        )
    }

    private companion object {
        val APP_A = ApplicationId("consumer-a")
        val APP_B = ApplicationId("consumer-b")
        val USE_CASE_DEFAULT = UseCaseId("document-analysis")
        val USE_CASE_SECONDARY = UseCaseId("summary")
        val USE_CASE_DISABLED_BINDING = UseCaseId("disabled-binding")
        val USE_CASE_DRAFT = UseCaseId("draft-use-case")
        val USE_CASE_OTHER_APP = UseCaseId("other-app-only")
    }
}
