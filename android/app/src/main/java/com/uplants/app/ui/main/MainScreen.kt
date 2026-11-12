package com.uplants.app.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.uplants.app.AppContainer
import com.uplants.app.R
import com.uplants.app.UPlantsApplication
import com.uplants.app.data.notifications.DeepLink
import com.uplants.app.ui.dashboard.DashboardScreen
import com.uplants.app.ui.dashboard.PostStatsScreen
import com.uplants.app.ui.navigation.DashboardRoute
import com.uplants.app.ui.navigation.PostStatsRoute
import com.uplants.app.ui.account.AccountScreen
import com.uplants.app.ui.feed.FeedScreen
import com.uplants.app.ui.lists.ListDetailScreen
import com.uplants.app.ui.lists.MyListsScreen
import com.uplants.app.ui.messages.ChatScreen
import com.uplants.app.ui.messages.InboxScreen
import com.uplants.app.ui.navigation.AccountRoute
import com.uplants.app.ui.navigation.ChatRoute
import com.uplants.app.ui.navigation.InboxRoute
import com.uplants.app.ui.navigation.ListDetailRoute
import com.uplants.app.ui.navigation.MyListsRoute
import com.uplants.app.ui.navigation.DELETED_POST_ID_KEY
import com.uplants.app.ui.navigation.FEED_NEEDS_REFRESH_KEY
import com.uplants.app.ui.navigation.FeedRoute
import com.uplants.app.ui.navigation.FollowListRoute
import com.uplants.app.ui.navigation.InterestsRoute
import com.uplants.app.ui.navigation.PostDetailRoute
import com.uplants.app.ui.navigation.ProfileRoute
import com.uplants.app.ui.navigation.PublishRoute
import com.uplants.app.ui.navigation.SearchRoute
import com.uplants.app.ui.navigation.VIEWED_POST_ID_KEY
import com.uplants.app.ui.post.PostDetailScreen
import com.uplants.app.ui.profile.FollowListScreen
import com.uplants.app.ui.profile.InterestsScreen
import com.uplants.app.ui.profile.ProfileScreen
import com.uplants.app.ui.publish.GuestPublishPrompt
import com.uplants.app.ui.publish.PublishScreen
import com.uplants.app.ui.search.SearchScreen
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
    TopLevelDestination(SearchRoute, SearchRoute::class, R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    TopLevelDestination(PublishRoute, PublishRoute::class, R.string.nav_publish, Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle),
    TopLevelDestination(AccountRoute, AccountRoute::class, R.string.nav_profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
)

/**
 * The app after login (or "continue as guest"): bottom navigation between the main tabs,
 * with detail screens (a post, a profile, ...) opened full screen on top.
 *
 * @param username null for visitors.
 * @param userId null for visitors.
 * @param onSignOut logs out a registered user, or sends a visitor to the login screen.
 */
@Composable
fun MainScreen(username: String?, userId: String?, onSignOut: () -> Unit) {
    val container = appContainer()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = topLevelDestinations.any { destination?.hasRoute(it.routeClass) == true }
    val isLoggedIn = userId != null

    val openPost: (String) -> Unit = { navController.navigate(PostDetailRoute(it)) }
    val openProfile: (String) -> Unit = { id ->
        if (id == userId) navController.navigateToTab(AccountRoute) else navController.navigate(ProfileRoute(id))
    }
    val openFollowList: (String, Boolean) -> Unit = { id, followers -> navController.navigate(FollowListRoute(id, followers)) }
    val openInterests: () -> Unit = { if (isLoggedIn) navController.navigate(InterestsRoute) else onSignOut() }
    val openMyLists: () -> Unit = { navController.navigate(MyListsRoute) }
    val openList: (String) -> Unit = { navController.navigate(ListDetailRoute(it)) }
    val openInbox: () -> Unit = { navController.navigate(InboxRoute) }
    val openChat: (String, String) -> Unit = { id, name -> navController.navigate(ChatRoute(id, name)) }
    val openDashboard: () -> Unit = { navController.navigate(DashboardRoute) }

    // Opened from a notification: go to the post / chat / profile it is about (registered users only).
    // Lifecycle-aware, so only the visible screen handles it.
    val pendingLink by container.pendingDeepLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        container.pendingDeepLink.value = null
        if (!isLoggedIn) return@LaunchedEffect
        when (link) {
            is DeepLink.Post -> openPost(link.postId)
            is DeepLink.Chat -> openChat(link.userId, link.name)
            is DeepLink.Profile -> openProfile(link.userId)
        }
    }

    // Android 13+: ask once for permission to show notifications.
    if (isLoggedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (!container.notifier.canNotify()) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                val changes = rememberPostChanges(entry)
                val needsRefresh by entry.savedStateHandle.getStateFlow(FEED_NEEDS_REFRESH_KEY, false).collectAsState()
                FeedScreen(
                    isLoggedIn = isLoggedIn,
                    onOpenPost = openPost,
                    onOpenProfile = openProfile,
                    onPickInterests = openInterests,
                    onOpenInbox = openInbox,
                    deletedPostId = changes.deleted,
                    onDeletedPostHandled = { entry.savedStateHandle[DELETED_POST_ID_KEY] = null },
                    refreshRequested = needsRefresh,
                    onRefreshHandled = { entry.savedStateHandle[FEED_NEEDS_REFRESH_KEY] = false },
                    viewedPostId = changes.viewed,
                    onViewedPostHandled = { entry.savedStateHandle[VIEWED_POST_ID_KEY] = null },
                )
            }
            composable<SearchRoute> { entry ->
                val changes = rememberPostChanges(entry)
                SearchScreen(
                    onOpenPost = openPost,
                    onOpenProfile = openProfile,
                    viewedPostId = changes.viewed,
                    deletedPostId = changes.deleted,
                    onPostChangesHandled = changes.clear,
                )
            }
            composable<PublishRoute> {
                if (isLoggedIn) {
                    PublishScreen(onPublished = { postId ->
                        // Show the new post; Back goes to the (refreshed) feed, and the form starts empty next time.
                        navController.getBackStackEntry<FeedRoute>().savedStateHandle[FEED_NEEDS_REFRESH_KEY] = true
                        navController.navigate(PostDetailRoute(postId)) {
                            popUpTo<FeedRoute>()
                        }
                    })
                } else {
                    GuestPublishPrompt(onLogin = onSignOut)
                }
            }
            composable<AccountRoute> { entry ->
                if (userId != null) {
                    val changes = rememberPostChanges(entry)
                    ProfileScreen(
                        userId = userId,
                        onBack = null,
                        onOpenPost = openPost,
                        onOpenFollowList = openFollowList,
                        onEditInterests = openInterests,
                        onOpenMyLists = openMyLists,
                        onOpenList = openList,
                        onMessage = openChat,
                        onOpenDashboard = openDashboard,
                        onSignOut = onSignOut,
                        onRequestLogin = onSignOut,
                        viewedPostId = changes.viewed,
                        deletedPostId = changes.deleted,
                        onPostChangesHandled = changes.clear,
                    )
                } else {
                    AccountScreen(username = username, onSignOut = onSignOut)
                }
            }
            composable<ProfileRoute> { entry ->
                val changes = rememberPostChanges(entry)
                ProfileScreen(
                    userId = entry.toRoute<ProfileRoute>().userId,
                    onBack = { navController.popBackStack() },
                    onOpenPost = openPost,
                    onOpenFollowList = openFollowList,
                    onEditInterests = openInterests,
                    onOpenMyLists = openMyLists,
                    onOpenList = openList,
                    onMessage = openChat,
                    onOpenDashboard = openDashboard,
                    onSignOut = onSignOut,
                    onRequestLogin = onSignOut,
                    viewedPostId = changes.viewed,
                    deletedPostId = changes.deleted,
                    onPostChangesHandled = changes.clear,
                )
            }
            composable<FollowListRoute> { entry ->
                val route = entry.toRoute<FollowListRoute>()
                FollowListScreen(
                    userId = route.userId,
                    followers = route.followers,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = openProfile,
                )
            }
            composable<InterestsRoute> {
                InterestsScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        // New interests change the personalised feed.
                        runCatching { navController.getBackStackEntry<FeedRoute>().savedStateHandle[FEED_NEEDS_REFRESH_KEY] = true }
                        navController.popBackStack()
                    },
                )
            }
            composable<MyListsRoute> {
                MyListsScreen(onBack = { navController.popBackStack() }, onOpenList = openList)
            }
            composable<ListDetailRoute> { entry ->
                ListDetailScreen(
                    listId = entry.toRoute<ListDetailRoute>().listId,
                    onBack = { navController.popBackStack() },
                    onOpenPost = openPost,
                    onOpenProfile = openProfile,
                )
            }
            composable<InboxRoute> {
                InboxScreen(onBack = { navController.popBackStack() }, onOpenChat = openChat)
            }
            composable<ChatRoute> { entry ->
                val route = entry.toRoute<ChatRoute>()
                ChatScreen(
                    userId = route.userId,
                    name = route.name,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = openProfile,
                    onOpenPost = openPost,
                )
            }
            composable<DashboardRoute> {
                if (userId != null) {
                    DashboardScreen(
                        userId = userId,
                        onBack = { navController.popBackStack() },
                        onOpenPostStats = { id, title -> navController.navigate(PostStatsRoute(id, title)) },
                    )
                }
            }
            composable<PostStatsRoute> { entry ->
                val route = entry.toRoute<PostStatsRoute>()
                PostStatsScreen(
                    postId = route.postId,
                    title = route.title,
                    onBack = { navController.popBackStack() },
                    onOpenPost = openPost,
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
                    onDeleted = { deletedId ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(DELETED_POST_ID_KEY, deletedId)
                        navController.popBackStack()
                    },
                    onRequestLogin = onSignOut,
                    onOpenProfile = openProfile,
                )
            }
        }
    }
}

@Composable
private fun appContainer(): AppContainer = (LocalContext.current.applicationContext as UPlantsApplication).container

/** A post this screen showed was viewed (counters may have changed) or deleted on its detail screen. */
private class PostChanges(val viewed: String?, val deleted: String?, val clear: () -> Unit)

@Composable
private fun rememberPostChanges(entry: NavBackStackEntry): PostChanges {
    val viewed by entry.savedStateHandle.getStateFlow<String?>(VIEWED_POST_ID_KEY, null).collectAsState()
    val deleted by entry.savedStateHandle.getStateFlow<String?>(DELETED_POST_ID_KEY, null).collectAsState()
    return PostChanges(viewed, deleted) {
        entry.savedStateHandle[VIEWED_POST_ID_KEY] = null
        entry.savedStateHandle[DELETED_POST_ID_KEY] = null
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
