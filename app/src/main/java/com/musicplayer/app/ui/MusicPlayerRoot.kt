package com.musicplayer.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicplayer.app.MainActivity
import com.musicplayer.app.ui.components.MiniPlayer
import com.musicplayer.app.ui.navigation.NavGraph
import com.musicplayer.app.ui.navigation.Screen
import com.musicplayer.app.ui.screens.nowplaying.NowPlayingScreen
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("Library", Screen.Library.route, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    BottomNavItem("Equalizer", Screen.Equalizer.route, Icons.Filled.Equalizer, Icons.Outlined.Equalizer),
    BottomNavItem("Search", Screen.Search.route, Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("Queue", Screen.Queue.route, Icons.AutoMirrored.Filled.QueueMusic, Icons.AutoMirrored.Outlined.QueueMusic),
)

@Composable
fun MusicPlayerRoot(
    viewModel: RootViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val playbackState by viewModel.playbackController.playbackState.collectAsState()
    val sourceRoute by viewModel.playbackController.sourceRoute.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.deletionHandler.onConfirmationResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        viewModel.deletionHandler.confirmationRequest.collect { deleteLauncher.launch(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.deletionHandler.feedback.collect { snackbarHostState.showSnackbar(it) }
    }

    // NowPlaying overlay state
    var showNowPlaying by remember { mutableStateOf(false) }
    val nowPlayingOffsetY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val screenHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    // On app launch, navigate to the source screen of the last played song
    var hasNavigatedToSource by remember { mutableStateOf(false) }
    LaunchedEffect(sourceRoute, playbackState.currentSong) {
        if (!hasNavigatedToSource && sourceRoute != null && playbackState.currentSong != null) {
            hasNavigatedToSource = true
            navController.navigate(sourceRoute!!) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Handle notification click -> open NowPlaying overlay
    val shouldNavigateToNowPlaying by MainActivity.navigateToNowPlaying.collectAsState()
    LaunchedEffect(shouldNavigateToNowPlaying) {
        if (shouldNavigateToNowPlaying) {
            MainActivity.consumeNowPlayingNavigation()
            if (!showNowPlaying) {
                nowPlayingOffsetY.snapTo(screenHeightPx)
                showNowPlaying = true
                nowPlayingOffsetY.animateTo(0f, tween(350))
            }
        }
    }

    val hideNavBarRoutes = setOf(Screen.Settings.route)
    val showNavBar = currentDestination?.route !in hideNavBarRoutes &&
            currentDestination?.route != null

    // On Library screen with a song playing: back opens NowPlaying first
    BackHandler(
        enabled = currentDestination?.route == Screen.Library.route &&
                playbackState.currentSong != null && !showNowPlaying
    ) {
        coroutineScope.launch {
            nowPlayingOffsetY.snapTo(screenHeightPx)
            showNowPlaying = true
            nowPlayingOffsetY.animateTo(0f, tween(350))
        }
    }

    // Back handler for dismissing NowPlaying overlay
    BackHandler(enabled = showNowPlaying) {
        coroutineScope.launch {
            nowPlayingOffsetY.animateTo(screenHeightPx, tween(300))
            showNowPlaying = false
        }
    }

    fun openNowPlaying() {
        if (!showNowPlaying) {
            coroutineScope.launch {
                nowPlayingOffsetY.snapTo(screenHeightPx)
                showNowPlaying = true
                nowPlayingOffsetY.animateTo(0f, tween(350))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val hasSong = playbackState.currentSong != null

                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                ) {
                    // MiniPlayer island when a song is loaded (hide when NowPlaying is fully visible)
                    if (hasSong && !showNowPlaying) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onPlayPauseClick = { viewModel.playbackController.togglePlayPause() },
                            onSkipNextClick = { viewModel.playbackController.skipToNext() },
                            onSkipPreviousClick = { viewModel.playbackController.skipToPrevious() },
                            onClick = { openNowPlaying() }
                        )
                    }

                    // Navigation bar always visible (except Settings and when NowPlaying is up)
                    if (showNavBar && !showNowPlaying) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == item.route
                                } == true

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavGraph(
                navController = navController,
                onNowPlayingClick = { openNowPlaying() },
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            )
        }

        // NowPlaying full-screen overlay
        if (showNowPlaying) {
            NowPlayingScreen(
                onBackClick = {
                    coroutineScope.launch {
                        nowPlayingOffsetY.animateTo(screenHeightPx, tween(300))
                        showNowPlaying = false
                        val target = sourceRoute ?: Screen.Queue.route
                        navController.navigate(target) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onDismiss = { showNowPlaying = false },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, nowPlayingOffsetY.value.roundToInt()) }
            )
        }
    }
}
