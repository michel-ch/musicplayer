package com.musicplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

@Composable
fun AlbumArtImage(
    uri: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 12.dp
) {
    val shape = RoundedCornerShape(cornerRadius)

    if (uri != null) {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = "Album art",
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Crop,
            loading = { DefaultAlbumArt(size = size, cornerRadius = cornerRadius) },
            error = { DefaultAlbumArt(size = size, cornerRadius = cornerRadius) },
            success = { SubcomposeAsyncImageContent() }
        )
    } else {
        DefaultAlbumArt(size = size, cornerRadius = cornerRadius, modifier = modifier)
    }
}

@Composable
private fun DefaultAlbumArt(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "No album art",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size / 2)
        )
    }
}
