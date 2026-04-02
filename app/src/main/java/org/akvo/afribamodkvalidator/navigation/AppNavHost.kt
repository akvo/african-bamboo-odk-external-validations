package org.akvo.afribamodkvalidator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.akvo.afribamodkvalidator.ui.screen.DownloadCompleteScreen
import org.akvo.afribamodkvalidator.ui.screen.HomeDashboardScreen
import org.akvo.afribamodkvalidator.ui.screen.LoadingScreen
import org.akvo.afribamodkvalidator.ui.screen.LoginScreen
import org.akvo.afribamodkvalidator.ui.screen.GeoMapViewScreen
import org.akvo.afribamodkvalidator.ui.screen.OfflineMapScreen
import org.akvo.afribamodkvalidator.ui.screen.SettingsScreen
import org.akvo.afribamodkvalidator.ui.screen.SubmissionDetailScreen
import org.akvo.afribamodkvalidator.ui.screen.SyncCompleteScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    val startDestination: Any = if (isLoggedIn) Home else Login

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<Login> {
            LoginScreen(
                onDownloadStart = {
                    navController.navigate(Loading(LoadingType.DOWNLOAD))
                }
            )
        }

        composable<Loading> { backStackEntry ->
            val route = backStackEntry.toRoute<Loading>()
            val message = when (route.type) {
                LoadingType.DOWNLOAD -> "Downloading data..."
                LoadingType.RESYNC -> "Syncing data..."
            }
            LoadingScreen(
                loadingType = route.type,
                message = message,
                onDownloadComplete = { totalEntries, latestDate ->
                    navController.navigate(
                        DownloadComplete(
                            totalEntries = totalEntries,
                            latestSubmissionDate = latestDate
                        )
                    ) {
                        popUpTo(Login) { inclusive = true }
                    }
                },
                onResyncComplete = { added, updated, rejected, latestTimestamp ->
                    navController.navigate(
                        SyncComplete(
                            addedRecords = added,
                            updatedRecords = updated,
                            rejectedRecords = rejected,
                            latestRecordTimestamp = latestTimestamp
                        )
                    ) {
                        popUpTo(Home)
                    }
                },
                onBackToLogin = {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<DownloadComplete> { backStackEntry ->
            val route = backStackEntry.toRoute<DownloadComplete>()
            DownloadCompleteScreen(
                totalEntries = route.totalEntries,
                latestSubmissionDate = route.latestSubmissionDate,
                onViewData = {
                    navController.navigate(Home) {
                        popUpTo<DownloadComplete> { inclusive = true }
                    }
                },
                onResyncData = {
                    navController.navigate(Loading(LoadingType.RESYNC))
                }
            )
        }

        composable<Home> {
            HomeDashboardScreen(
                onResyncClick = {
                    navController.navigate(Loading(LoadingType.RESYNC))
                },
                onLogout = {
                    navController.navigate(Login) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSubmissionClick = { uuid ->
                    navController.navigate(SubmissionDetail(uuid))
                },
                onOfflineMapsClick = {
                    navController.navigate(OfflineMap)
                },
                onSettingsClick = {
                    navController.navigate(Settings)
                }
            )
        }

        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<SubmissionDetail> {
            SubmissionDetailScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onViewGeoOnMap = { uuid, fieldKey ->
                    navController.navigate(GeoMapView(uuid, fieldKey))
                }
            )
        }

        composable<GeoMapView> {
            GeoMapViewScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<SyncComplete> { backStackEntry ->
            val route = backStackEntry.toRoute<SyncComplete>()
            SyncCompleteScreen(
                addedRecords = route.addedRecords,
                updatedRecords = route.updatedRecords,
                rejectedRecords = route.rejectedRecords,
                latestRecordTimestamp = route.latestRecordTimestamp,
                onReturnToDashboard = {
                    navController.navigate(Home) {
                        popUpTo(Home) { inclusive = true }
                    }
                }
            )
        }

        composable<OfflineMap> {
            OfflineMapScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
