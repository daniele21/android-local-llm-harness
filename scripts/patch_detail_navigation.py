from pathlib import Path


PATH = Path(
    "apps/local-llm-phone-test/src/main/kotlin/"
    "io/github/daniele21/localllm/phonetest/MainActivity.kt"
)


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


text = PATH.read_text()

text = replace_once(
    text,
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\n"
    "import androidx.compose.runtime.DisposableEffect\n"
    "import androidx.compose.runtime.LaunchedEffect\n",
    "compose effects imports",
)
text = replace_once(
    text,
    "import androidx.navigation.compose.NavHost\n",
    "import androidx.navigation.NavType\nimport androidx.navigation.compose.NavHost\n",
    "navigation type import",
)
text = replace_once(
    text,
    "import androidx.navigation.compose.rememberNavController\n",
    "import androidx.navigation.compose.rememberNavController\n"
    "import androidx.navigation.navArgument\n",
    "navigation argument import",
)

text = replace_once(
    text,
    """        val backStackEntry by navController.currentBackStackEntryAsState()
        val destination = HarnessDestination.fromRoute(backStackEntry?.destination?.route)
        val expanded = LocalConfiguration.current.screenWidthDp >= 720
""",
    """        val backStackEntry by navController.currentBackStackEntryAsState()
        val shellState = HarnessRoutes.shellState(backStackEntry?.destination?.route)
        val destination = shellState.destination
        val expanded = LocalConfiguration.current.screenWidthDp >= 720
""",
    "shell route state",
)
text = replace_once(
    text,
    """        val navigate: (HarnessDestination) -> Unit = { target ->
            navController.navigate(target.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(HarnessDestination.OVERVIEW.route) { saveState = true }
            }
        }

        Scaffold(
""",
    """        val navigate: (HarnessDestination) -> Unit = { target ->
            navController.navigate(target.route) {
                launchSingleTop = true
                restoreState = true
                popUpTo(HarnessDestination.OVERVIEW.route) { saveState = true }
            }
        }
        val navigateToSettings: () -> Unit = {
            navController.navigate(HarnessDestination.SETTINGS.route) {
                launchSingleTop = true
            }
        }

        Scaffold(
""",
    "settings navigation",
)
text = replace_once(
    text,
    """            topBar = {
                HarnessTopBar(
                    destination = destination,
                    onOpenSettings = { navigate(HarnessDestination.SETTINGS) },
                    onNavigateBack = {
                        if (!navController.popBackStack()) navigate(HarnessDestination.OVERVIEW)
                    },
                )
            },
            bottomBar = {
                if (!expanded && destination != HarnessDestination.SETTINGS) {
                    HarnessBottomBar(destination = destination, onNavigate = navigate)
                }
            },
""",
    """            topBar = {
                if (shellState.isDetail) {
                    HarnessDetailTopBar(
                        title = requireNotNull(shellState.detailTitle),
                        subtitle = shellState.detailSubtitle.orEmpty(),
                        onNavigateBack = { navController.popBackStack() },
                    )
                } else {
                    HarnessTopBar(
                        destination = destination,
                        onOpenSettings = navigateToSettings,
                        onNavigateBack = {
                            if (!navController.popBackStack()) navigate(HarnessDestination.OVERVIEW)
                        },
                    )
                }
            },
            bottomBar = {
                if (!expanded && shellState.showBottomNavigation) {
                    HarnessBottomBar(destination = destination, onNavigate = navigate)
                }
            },
""",
    "detail shell chrome",
)
text = replace_once(
    text,
    """                            onClick = { navigate(HarnessDestination.SETTINGS) },
""",
    """                            onClick = navigateToSettings,
""",
    "expanded settings navigation",
)
text = replace_once(
    text,
    """                    composable(HarnessDestination.DIAGNOSTICS.route) { DiagnosticsScreen() }
                    composable(HarnessDestination.SETTINGS.route) {
                        SettingsScreen(
                            onOpenDiagnostics = { navigate(HarnessDestination.DIAGNOSTICS) },
                        )
                    }
""",
    """                    composable(HarnessDestination.DIAGNOSTICS.route) {
                        DiagnosticsScreen(
                            onOpenRequestTimeline = { requestId ->
                                navController.navigate(HarnessRoutes.requestTimeline(requestId))
                            },
                        )
                    }
                    composable(HarnessDestination.SETTINGS.route) {
                        SettingsScreen(
                            onOpenPrivacy = {
                                navController.navigate(HarnessSettingsDetail.PRIVACY.route)
                            },
                            onOpenStorage = {
                                navController.navigate(HarnessSettingsDetail.STORAGE.route)
                            },
                            onOpenBuild = {
                                navController.navigate(HarnessSettingsDetail.BUILD.route)
                            },
                            onOpenDeveloperTools = {
                                navController.navigate(HarnessSettingsDetail.DEVELOPER_TOOLS.route)
                            },
                        )
                    }
                    composable(HarnessSettingsDetail.PRIVACY.route) {
                        PrivacyDetailScreen()
                    }
                    composable(HarnessSettingsDetail.STORAGE.route) {
                        StorageDetailScreen(
                            importedModel = importedModel,
                            onOpenModels = { navigate(HarnessDestination.MODELS) },
                        )
                    }
                    composable(HarnessSettingsDetail.BUILD.route) {
                        BuildDetailScreen(
                            versionName = appVersionName(),
                            versionCode = BuildConfig.VERSION_CODE.toString(),
                            applicationId = BuildConfig.APPLICATION_ID,
                        )
                    }
                    composable(HarnessSettingsDetail.DEVELOPER_TOOLS.route) {
                        DeveloperToolsDetailScreen(
                            onOpenHealth = {
                                selectDiagnosticsSection(DiagnosticsSection.HEALTH)
                                navigate(HarnessDestination.DIAGNOSTICS)
                            },
                            onOpenLogs = {
                                selectDiagnosticsSection(DiagnosticsSection.LOGS)
                                navigate(HarnessDestination.DIAGNOSTICS)
                            },
                            onOpenPhysicalValidation = {
                                navController.navigate(HarnessSettingsDetail.PHYSICAL_VALIDATION.route)
                            },
                        )
                    }
                    composable(HarnessSettingsDetail.PHYSICAL_VALIDATION.route) {
                        PhysicalValidationDetailScreen(
                            modelAvailable = importedModel != null,
                            busy = isBusy() || diagnosticActionRunning(),
                            latestReport = latestReport,
                            onRunValidation = ::runPhysicalValidation,
                            onCopyReport = ::copyReport,
                            onShareReport = ::shareReport,
                        )
                    }
                    composable(
                        route = HarnessRoutes.REQUEST_TIMELINE_PATTERN,
                        arguments = listOf(
                            navArgument(HarnessRoutes.REQUEST_ID_ARGUMENT) {
                                type = NavType.StringType
                            },
                        ),
                    ) { entry ->
                        val requestId = HarnessRoutes.decodeRequestId(
                            entry.arguments?.getString(HarnessRoutes.REQUEST_ID_ARGUMENT),
                        )
                        LaunchedEffect(requestId) {
                            if (requestId != null) openRequestTimeline(requestId)
                        }
                        DisposableEffect(requestId) {
                            onDispose { closeRequestTimeline() }
                        }
                        RequestTimelineDetailScreen(
                            timeline = selectedRequestTimeline,
                            onCopyLog = ::copyLog,
                        )
                    }
""",
    "detail destinations",
)

text = replace_once(
    text,
    """    @Composable
    private fun DiagnosticsScreen() {
""",
    """    @Composable
    private fun DiagnosticsScreen(onOpenRequestTimeline: (String) -> Unit) {
""",
    "diagnostics callback",
)
text = replace_once(
    text,
    """                    runDiagnostics()
""",
    """                    runDiagnostics(onOpenRequestTimeline)
""",
    "run timeline callback",
)
text = replace_once(
    text,
    """                        onOpenTimeline = ::openRequestTimeline,
""",
    """                        onOpenTimeline = onOpenRequestTimeline,
""",
    "log timeline callback",
)
text = replace_once(
    text,
    """    private fun androidx.compose.foundation.lazy.LazyListScope.runDiagnostics() {
""",
    """    private fun androidx.compose.foundation.lazy.LazyListScope.runDiagnostics(
        onOpenRequestTimeline: (String) -> Unit,
    ) {
""",
    "run diagnostics signature",
)
text = replace_once(
    text,
    """                HarnessSecondaryButton("View request timeline") {
                    openRequestTimeline(run.requestId)
                }
""",
    """                HarnessSecondaryButton("View request timeline") {
                    onOpenRequestTimeline(run.requestId)
                }
""",
    "run timeline action",
)

text = replace_once(
    text,
    """    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics() {
""",
    """    private fun runPhysicalValidation() {
        afterPlaygroundRuntimeReleased {
            latestReport = ""
            operationStatus = "Starting validation…"
            controller.runFullValidation()
        }
    }

    private fun androidx.compose.foundation.lazy.LazyListScope.validationDiagnostics() {
""",
    "physical validation action",
)
text = replace_once(
    text,
    """                HarnessPrimaryButton(
                    "Run full validation",
                    enabled = importedModel != null && !isBusy() && !diagnosticActionRunning(),
                ) {
                    afterPlaygroundRuntimeReleased {
                        latestReport = ""
                        operationStatus = "Starting validation…"
                        controller.runFullValidation()
                    }
                }
""",
    """                HarnessPrimaryButton(
                    "Run full validation",
                    enabled = importedModel != null && !isBusy() && !diagnosticActionRunning(),
                    onClick = ::runPhysicalValidation,
                )
""",
    "reuse physical validation action",
)

text = replace_once(
    text,
    """    @Composable
    private fun SettingsScreen(onOpenDiagnostics: () -> Unit) {
""",
    """    @Composable
    private fun SettingsScreen(
        onOpenPrivacy: () -> Unit,
        onOpenStorage: () -> Unit,
        onOpenBuild: () -> Unit,
        onOpenDeveloperTools: () -> Unit,
    ) {
""",
    "settings callbacks",
)
text = replace_once(
    text,
    """                    trailing = "On-device",
                )
""",
    """                    trailing = "On-device",
                    onClick = onOpenPrivacy,
                )
""",
    "privacy detail action",
)
text = replace_once(
    text,
    """                    trailing = importedModel?.let { "${formatBytes(it.sizeBytes)} used" } ?: "Empty",
                )
""",
    """                    trailing = importedModel?.let { "${formatBytes(it.sizeBytes)} used" } ?: "Empty",
                    onClick = onOpenStorage,
                )
""",
    "storage detail action",
)
text = replace_once(
    text,
    """                    trailing = appVersionName(),
                )
""",
    """                    trailing = appVersionName(),
                    onClick = onOpenBuild,
                )
""",
    "build detail action",
)
text = replace_once(
    text,
    """                    onClick = onOpenDiagnostics,
""",
    """                    onClick = onOpenDeveloperTools,
""",
    "developer tools detail action",
)

PATH.write_text(text)
