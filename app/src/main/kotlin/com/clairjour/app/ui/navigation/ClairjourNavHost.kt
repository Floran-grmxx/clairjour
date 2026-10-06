package com.clairjour.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.clairjour.app.data.AppContainer
import com.clairjour.app.ui.screen.addiction.AddictionEditScreen
import com.clairjour.app.ui.screen.crisis.CrisisScreen
import com.clairjour.app.ui.screen.home.HomeScreen
import com.clairjour.app.ui.screen.journal.JournalEditorScreen
import com.clairjour.app.ui.screen.journal.JournalScreen
import com.clairjour.app.ui.screen.onboarding.OnboardingScreen
import com.clairjour.app.ui.screen.settings.SettingsScreen
import com.clairjour.app.ui.screen.shield.ShieldScreen
import com.clairjour.app.ui.screen.stats.StatsScreen
import kotlinx.datetime.LocalDate

@Composable
fun ClairjourNavHost(
    navController: NavHostController,
    container: AppContainer,
    startDestination: String,
    contentPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.ONBOARDING) {
            OnboardingScreen(
                container = container,
                onDone = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Destinations.HOME) {
            HomeScreen(
                container = container,
                contentPadding = contentPadding,
                onAddAddiction = { navController.navigate(Destinations.addictionEdit()) },
                onOpenJournalEditor = { navController.navigate(Destinations.journalEditor()) },
                onOpenCrisis = { addictionId ->
                    navController.navigate(Destinations.crisis(addictionId))
                }
            )
        }
        composable(
            route = Destinations.CRISIS,
            arguments = listOf(navArgument("addictionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val addictionId = backStackEntry.arguments?.getString("addictionId")
            CrisisScreen(
                container = container,
                addictionId = addictionId,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Destinations.JOURNAL) {
            JournalScreen(
                container = container,
                contentPadding = contentPadding,
                onOpenEditor = { date -> navController.navigate(Destinations.journalEditor(date)) }
            )
        }
        composable(
            route = Destinations.JOURNAL_EDITOR,
            arguments = listOf(navArgument("date") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val dateStr = backStackEntry.arguments?.getString("date")
            val date = dateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            JournalEditorScreen(
                container = container,
                date = date,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Destinations.STATS) {
            StatsScreen(
                container = container,
                contentPadding = contentPadding
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                container = container,
                contentPadding = contentPadding,
                onEditAddiction = { navController.navigate(Destinations.addictionEdit(it)) },
                onAddAddiction = { navController.navigate(Destinations.addictionEdit()) },
                onOpenShield = { navController.navigate(Destinations.SHIELD) }
            )
        }
        composable(Destinations.SHIELD) {
            ShieldScreen(
                container = container,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Destinations.ADDICTION_EDIT,
            arguments = listOf(navArgument("addictionId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("addictionId")
            AddictionEditScreen(
                container = container,
                addictionId = id,
                onDone = { navController.popBackStack() }
            )
        }
    }
}
