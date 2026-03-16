package com.musicplayer.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.musicplayer.app.ui.screens.album.AlbumDetailScreen
import com.musicplayer.app.ui.screens.albums.AlbumsListScreen
import com.musicplayer.app.ui.screens.artist.ArtistDetailScreen
import com.musicplayer.app.ui.screens.artists.ArtistsListScreen
import com.musicplayer.app.ui.screens.equalizer.EqualizerScreen
import com.musicplayer.app.ui.screens.folder.FolderBrowserScreen
import com.musicplayer.app.ui.screens.genre.GenreDetailScreen
import com.musicplayer.app.ui.screens.genre.GenreListScreen
import com.musicplayer.app.ui.screens.library.LibraryScreen
import com.musicplayer.app.ui.screens.playlist.PlaylistScreen
import com.musicplayer.app.ui.screens.queue.QueueScreen
import com.musicplayer.app.ui.screens.search.SearchScreen
import com.musicplayer.app.ui.screens.settings.SettingsScreen
import com.musicplayer.app.ui.screens.songs.AllSongsScreen
import com.musicplayer.app.ui.screens.albumartist.AlbumArtistDetailScreen
import com.musicplayer.app.ui.screens.albumartist.AlbumArtistListScreen
import com.musicplayer.app.ui.screens.composer.ComposerDetailScreen
import com.musicplayer.app.ui.screens.composer.ComposerListScreen
import com.musicplayer.app.ui.screens.folder.FolderHierarchyScreen
import com.musicplayer.app.ui.screens.streams.StreamsScreen
import com.musicplayer.app.ui.screens.year.YearDetailScreen
import com.musicplayer.app.ui.screens.year.YearListScreen

// Forward navigation: new screen slides in from right
private val slideEnter: EnterTransition = slideInHorizontally(
    initialOffsetX = { it },
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

// Current screen slides out to left when navigating forward
private val slideExit: ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 3 },
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(200))

// Going back: previous screen slides in from left
private val slidePopEnter: EnterTransition = slideInHorizontally(
    initialOffsetX = { -it / 3 },
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

// Current screen slides out to right when going back
private val slidePopExit: ExitTransition = slideOutHorizontally(
    targetOffsetX = { it },
    animationSpec = tween(300)
) + fadeOut(animationSpec = tween(200))

@Composable
fun NavGraph(
    navController: NavHostController,
    onNowPlayingClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier,
        enterTransition = { slideEnter },
        exitTransition = { slideExit },
        popEnterTransition = { slidePopEnter },
        popExitTransition = { slidePopExit }
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                onAllSongsClick = { navController.navigate(Screen.AllSongs.route) },
                onFoldersClick = { navController.navigate(Screen.Folders.route) },
                onAlbumsClick = { navController.navigate(Screen.AlbumsList.route) },
                onArtistsClick = { navController.navigate(Screen.ArtistsList.route) },
                onGenresClick = { navController.navigate(Screen.GenreList.route) },
                onYearsClick = { navController.navigate(Screen.YearList.route) },
                onPlaylistsClick = { navController.navigate(Screen.Playlists.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onFoldersHierarchyClick = { navController.navigate(Screen.FoldersHierarchy.route) },
                onAlbumArtistsClick = { navController.navigate(Screen.AlbumArtistsList.route) },
                onComposersClick = { navController.navigate(Screen.ComposerList.route) },
                onStreamsClick = { navController.navigate(Screen.Streams.route) }
            )
        }

        composable(Screen.AllSongs.route) {
            AllSongsScreen(
                onBackClick = {
                    navController.popBackStack(Screen.Library.route, inclusive = false)
                }
            )
        }

        composable(Screen.AlbumsList.route) {
            AlbumsListScreen(
                onBackClick = { navController.popBackStack() },
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                }
            )
        }

        composable(Screen.ArtistsList.route) {
            ArtistsListScreen(
                onBackClick = { navController.popBackStack() },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(Screen.GenreList.route) {
            GenreListScreen(
                onBackClick = { navController.popBackStack() },
                onGenreClick = { genreName ->
                    navController.navigate(Screen.GenreDetail.createRoute(genreName))
                }
            )
        }

        composable(
            route = Screen.GenreDetail.route,
            arguments = listOf(navArgument("genreName") { type = NavType.StringType })
        ) { backStackEntry ->
            val genreName = backStackEntry.arguments?.getString("genreName") ?: return@composable
            GenreDetailScreen(
                genreName = genreName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.YearList.route) {
            YearListScreen(
                onBackClick = { navController.popBackStack() },
                onYearClick = { year ->
                    navController.navigate(Screen.YearDetail.createRoute(year))
                }
            )
        }

        composable(
            route = Screen.YearDetail.route,
            arguments = listOf(navArgument("year") { type = NavType.IntType })
        ) { backStackEntry ->
            val year = backStackEntry.arguments?.getInt("year") ?: return@composable
            YearDetailScreen(
                year = year,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Folders.route) {
            FolderBrowserScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Playlists.route) {
            PlaylistScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Equalizer.route) {
            EqualizerScreen()
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                },
                onFolderClick = { /* Navigate to folders with selected folder */ },
                onAlbumArtistClick = { artistName ->
                    navController.navigate(Screen.AlbumArtistDetail.createRoute(artistName))
                },
                onComposerClick = { composerName ->
                    navController.navigate(Screen.ComposerDetail.createRoute(composerName))
                }
            )
        }

        composable(Screen.Queue.route) {
            QueueScreen(
                onNowPlayingClick = onNowPlayingClick
            )
        }

        composable(
            Screen.Settings.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(250))
            },
            popEnterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(350)
                ) + fadeOut(animationSpec = tween(250))
            }
        ) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
            ArtistDetailScreen(
                artistName = artistName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.FoldersHierarchy.route) {
            FolderHierarchyScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.AlbumArtistsList.route) {
            AlbumArtistListScreen(
                onBackClick = { navController.popBackStack() },
                onArtistClick = { artistName ->
                    navController.navigate(Screen.AlbumArtistDetail.createRoute(artistName))
                }
            )
        }

        composable(
            route = Screen.AlbumArtistDetail.route,
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString("artistName") ?: return@composable
            AlbumArtistDetailScreen(
                artistName = artistName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ComposerList.route) {
            ComposerListScreen(
                onBackClick = { navController.popBackStack() },
                onComposerClick = { composerName ->
                    navController.navigate(Screen.ComposerDetail.createRoute(composerName))
                }
            )
        }

        composable(
            route = Screen.ComposerDetail.route,
            arguments = listOf(navArgument("composerName") { type = NavType.StringType })
        ) { backStackEntry ->
            val composerName = backStackEntry.arguments?.getString("composerName") ?: return@composable
            ComposerDetailScreen(
                composerName = composerName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Streams.route) {
            StreamsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
