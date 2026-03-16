package com.musicplayer.app.ui.navigation

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Folders : Screen("folders")
    data object Playlists : Screen("playlists")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object Equalizer : Screen("equalizer")
    data object Search : Screen("search")
    data object Queue : Screen("queue")
    data object AllSongs : Screen("all_songs")
    data object AlbumsList : Screen("albums_list")
    data object ArtistsList : Screen("artists_list")
    data object GenreList : Screen("genre_list")
    data object YearList : Screen("year_list")
    data object FoldersHierarchy : Screen("folders_hierarchy")
    data object AlbumArtistsList : Screen("album_artists_list")
    data object ComposerList : Screen("composer_list")
    data object Streams : Screen("streams")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist/{artistName}") {
        fun createRoute(artistName: String) = "artist/$artistName"
    }
    data object GenreDetail : Screen("genre/{genreName}") {
        fun createRoute(genreName: String) = "genre/$genreName"
    }
    data object YearDetail : Screen("year/{year}") {
        fun createRoute(year: Int) = "year/$year"
    }
    data object AlbumArtistDetail : Screen("album_artist/{artistName}") {
        fun createRoute(artistName: String) = "album_artist/$artistName"
    }
    data object ComposerDetail : Screen("composer/{composerName}") {
        fun createRoute(composerName: String) = "composer/$composerName"
    }
}
