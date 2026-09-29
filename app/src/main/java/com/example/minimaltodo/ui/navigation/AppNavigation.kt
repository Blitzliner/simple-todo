package com.example.minimaltodo.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.minimaltodo.R
import com.example.minimaltodo.data.preferences.ThemeMode
import com.example.minimaltodo.ui.MainViewModel
import com.example.minimaltodo.ui.editor.EditorScreen
import com.example.minimaltodo.ui.favorites.FavoritesScreen
import com.example.minimaltodo.ui.home.HomeScreen
import com.example.minimaltodo.ui.search.SearchScreen
import com.example.minimaltodo.ui.settings.SettingsScreen
import com.example.minimaltodo.ui.share.ShareInDialog
import com.example.minimaltodo.ui.theme.AppIcons
import com.example.minimaltodo.ui.theme.MinimalTodoTheme
import com.example.minimaltodo.ui.today.TodayScreen
import com.example.minimaltodo.ui.welcome.WelcomeScreen

/** Flache Navigation: drei Hauptziele + Suche, Editor, Gruppe, Einstellungen. */
object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val FAVORITES = "favorites"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val GROUP = "group/{id}"
    const val EDITOR = "editor/{id}"

    fun group(id: String) = "group/$id"
    fun editor(id: String) = "editor/$id"
}

private data class TopLevel(val route: String, val label: Int, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevel(Routes.TODAY, R.string.nav_today, AppIcons.Today),
    TopLevel(Routes.FAVORITES, R.string.nav_favorites, AppIcons.StarOutline),
)

@Composable
fun MinimalTodoRoot(
    mainViewModel: MainViewModel,
    onDarkThemeResolved: (Boolean) -> Unit,
    onShareHandled: (saved: Boolean) -> Unit,
) {
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val sharedText by mainViewModel.sharedText.collectAsStateWithLifecycle()
    val shareMessage by mainViewModel.message.collectAsStateWithLifecycle()
    val currentSettings = settings

    val dark = when (currentSettings?.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val onDark by rememberUpdatedState(onDarkThemeResolved)
    LaunchedEffect(dark) { onDark(dark) }

    MinimalTodoTheme(darkTheme = dark) {
        when {
            // Einstellungen werden noch geladen (wenige Millisekunden) → leere Fläche statt Flackern.
            currentSettings == null -> Surface(Modifier.fillMaxSize()) {}
            !currentSettings.onboardingDone && sharedText == null -> WelcomeScreen(onStart = mainViewModel::completeOnboarding)
            else -> AppNavHost()
        }

        sharedText?.let { text ->
            ShareInDialog(
                sharedText = text,
                onSave = mainViewModel::saveShared,
                onDismiss = {
                    mainViewModel.dismissShared()
                    onShareHandled(false)
                },
            )
        }
    }

    val handled by rememberUpdatedState(onShareHandled)
    LaunchedEffect(shareMessage) {
        val m = shareMessage ?: return@LaunchedEffect
        mainViewModel.messageShown()
        handled(m == MainViewModel.SharedMessage.SAVED)
    }
}

@Composable
private fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || topLevel.any { it.route == currentRoute }

    val openEditor: (String) -> Unit = { navController.navigate(Routes.editor(it)) }
    val openGroup: (String) -> Unit = { navController.navigate(Routes.group(it)) }
    val openSearch: () -> Unit = { navController.navigate(Routes.SEARCH) { launchSingleTop = true } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    rootId = null,
                    onOpenEditor = openEditor,
                    onOpenGroup = openGroup,
                    onOpenSearch = openSearch,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onBack = null,
                )
            }
            composable(Routes.GROUP, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val back = dropUnlessResumed { navController.popBackStack() }
                val id = entry.arguments?.getString("id").orEmpty()
                HomeScreen(
                    rootId = id,
                    onOpenEditor = openEditor,
                    onOpenGroup = openGroup,
                    onOpenSearch = openSearch,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onBack = back,
                )
            }
            composable(Routes.TODAY) {
                TodayScreen(onOpenEditor = openEditor, onOpenGroup = openGroup, onOpenSearch = openSearch)
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(onOpenEditor = openEditor, onOpenGroup = openGroup, onOpenSearch = openSearch)
            }
            composable(Routes.SEARCH) {
                val back = dropUnlessResumed { navController.popBackStack() }
                SearchScreen(onBack = back, onOpenEditor = openEditor, onOpenGroup = openGroup)
            }
            composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val back = dropUnlessResumed { navController.popBackStack() }
                val id = entry.arguments?.getString("id").orEmpty()
                EditorScreen(itemId = id, onClose = back, onOpenEditor = openEditor)
            }
            composable(Routes.SETTINGS) {
                val back = dropUnlessResumed { navController.popBackStack() }
                SettingsScreen(onBack = back)
            }
        }
    }
}
