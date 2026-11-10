package com.uplants.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.uplants.app.R
import com.uplants.app.ui.account.AccountScreen
import com.uplants.app.ui.feed.FeedScreen
import com.uplants.app.ui.navigation.AccountRoute
import com.uplants.app.ui.navigation.DELETED_POST_ID_KEY
import com.uplants.app.ui.navigation.FeedRoute
import com.uplants.app.ui.navigation.PostDetailRoute
import com.uplants.app.ui.navigation.VIEWED_POST_ID_KEY
import com.uplants.app.ui.post.PostDetailScreen
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(FeedRoute, FeedRoute::class, R.string.nav_feed, Icons.Outlined.Home, Icons.Filled.Home),
    TopLevelDestination(AccountRoute, AccountRoute::class, R.string.nav_account, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

/**
 * The app after login (or "continue as guest"): bottom navigation between the main tabs,
 * with detail screens (e.g. a post) opened full screen on top.
 *
 * @param username null for visitors.
 * @param onSignOut logs out a registered user, or sends a visitor to the login screen.
 */
@Composable
fun MainScreen(username: String?, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination?.hasRoute(it.routeClass) == true }
    val isLoggedIn = username != null

    Scaffold(
        // Each screen draws its own top bar; this Scaffold only adds the bottom bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { item ->
                        val selected = destination?.hasRoute(item.routeClass) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTab(item.route) },
                            icon = { Icon(if (selected) item.selectedIcon else item.icon, null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = FeedRoute, modifier = Modifier.padding(padding)) {
            composable<FeedRoute> { entry ->
                val deletedPostId by entry.savedStateHandle.getStateFlow<String?>(DELETED_POST_ID_KEY, null).collectAsState()
                val viewedPostId by entry.savedStateHandle.getStateFlow<String?>(VIEWED_POST_ID_KEY, null).collectAsState()
                FeedScreen(
                    isLoggedIn = isLoggedIn,
                    onOpenPost = { navController.navigate(PostDetailRoute(it)) },
                    deletedPostId = deletedPostId,
                    onDeletedPostHandled = { entry.savedStateHandle[DELETED_POST_ID_KEY] = null },
                    viewedPostId = viewedPostId,
                    onViewedPostHandled = { entry.savedStateHandle[VIEWED_POST_ID_KEY] = null },
                )
            }
            composable<PostDetailRoute> { entry ->
                val postId = entry.toRoute<PostDetailRoute>().postId
                // Whatever happens here (likes, comments, views), the screen below refreshes this post on return.
                val screenBelow = remember(entry) { navController.previousBackStackEntry }
                DisposableEffect(postId) {
                    onDispose { screenBelow?.savedStateHandle?.set(VIEWED_POST_ID_KEY, postId) }
                }
                PostDetailScreen(
                    onBack = { navController.popBackStack() },
                    onDeleted = { postId ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(DELETED_POST_ID_KEY, postId)
                        navController.popBackStack()
                    },
                    onRequestLogin = onSignOut,
                )
            }
            composable<AccountRoute> {
                AccountScreen(username = username, onSignOut = onSignOut)
            }
        }
    }
}

/** Switch tab keeping one copy of each tab on the back stack, and restore its state. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
