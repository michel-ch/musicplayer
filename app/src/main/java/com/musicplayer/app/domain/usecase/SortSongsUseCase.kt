package com.musicplayer.app.domain.usecase

import com.musicplayer.app.domain.model.Song
import com.musicplayer.app.domain.model.SortOption
import javax.inject.Inject

class SortSongsUseCase @Inject constructor() {

    private val naturalOrder: Comparator<String> = Comparator { a, b -> compareNatural(a, b) }

    operator fun invoke(songs: List<Song>, sortOption: SortOption, reverse: Boolean = false): List<Song> {
        val sorted = when (sortOption) {
            SortOption.TITLE_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> songs.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            SortOption.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            SortOption.ALBUM_DESC -> songs.sortedByDescending { it.album.lowercase() }
            SortOption.DURATION_ASC -> songs.sortedBy { it.duration }
            SortOption.DURATION_DESC -> songs.sortedByDescending { it.duration }
            SortOption.DATE_ADDED_ASC -> songs.sortedBy { it.dateAdded }
            SortOption.DATE_ADDED_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOption.DATE_MODIFIED_ASC -> songs.sortedBy { it.dateModified }
            SortOption.DATE_MODIFIED_DESC -> songs.sortedByDescending { it.dateModified }
            SortOption.SIZE_ASC -> songs.sortedBy { it.size }
            SortOption.SIZE_DESC -> songs.sortedByDescending { it.size }
            SortOption.TRACK_NUMBER -> songs.sortedWith(
                compareBy({ it.album.lowercase() }, { it.trackNumber })
            )
            SortOption.DISC_AND_TRACK -> songs.sortedWith(
                compareBy({ it.album.lowercase() }, { it.discNumber }, { it.trackNumber })
            )
            SortOption.FILENAME_ASC -> songs.sortedWith(compareBy(naturalOrder) { it.fileName })
            SortOption.FILENAME_DESC -> songs.sortedWith(compareByDescending(naturalOrder) { it.fileName })
            SortOption.YEAR_ASC -> songs.sortedBy { it.year }
            SortOption.YEAR_DESC -> songs.sortedByDescending { it.year }
            SortOption.GENRE -> songs.sortedBy { it.genre.lowercase() }
            SortOption.COMPOSER_ASC -> songs.sortedBy { it.composer.lowercase() }
            SortOption.COMPOSER_DESC -> songs.sortedByDescending { it.composer.lowercase() }
            SortOption.FORMAT -> songs.sortedBy { it.fileFormat }
            SortOption.SHUFFLE, SortOption.SHUFFLE_ORDER -> songs.shuffled()
        }
        return if (reverse) sorted.reversed() else sorted
    }

    private fun compareNatural(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numA = a.substring(i, endA).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, endB).trimStart('0').ifEmpty { "0" }
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return a.length - b.length
    }
}
