package io.github.daniele21.localllm.phonetest

import io.github.daniele21.localllm.models.HostControlPlaneStore

/** Phone composition boundary that keeps persistent control-plane and read-only runtime sources explicit. */
internal class HarnessPhoneControlPlaneAccess(
    store: HostControlPlaneStore,
    val applicationsRuntimeSource: HarnessApplicationsRuntimeSource,
) : HostControlPlaneStore by store
