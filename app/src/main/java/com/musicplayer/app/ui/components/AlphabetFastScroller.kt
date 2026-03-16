package com.musicplayer.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val ALPHABET = ('A'..'Z').toList() + '#'

@Composable
fun AlphabetFastScroller(
    listState: LazyListState,
    items: List<String>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .padding(vertical = 4.dp)
            .pointerInput(items) {
                detectTapGestures { offset ->
                    val index = (offset.y / (size.height.toFloat() / ALPHABET.size))
                        .toInt()
                        .coerceIn(0, ALPHABET.lastIndex)
                    val letter = ALPHABET[index]
                    val targetIndex = findFirstIndexForLetter(items, letter)
                    if (targetIndex >= 0) {
                        scope.launch { listState.scrollToItem(targetIndex) }
                    }
                }
            }
            .pointerInput(items) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val index = (change.position.y / (size.height.toFloat() / ALPHABET.size))
                        .toInt()
                        .coerceIn(0, ALPHABET.lastIndex)
                    val letter = ALPHABET[index]
                    val targetIndex = findFirstIndexForLetter(items, letter)
                    if (targetIndex >= 0) {
                        scope.launch { listState.scrollToItem(targetIndex) }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ALPHABET.forEach { letter ->
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun findFirstIndexForLetter(items: List<String>, letter: Char): Int {
    if (letter == '#') {
        return items.indexOfFirst { it.firstOrNull()?.isLetter() != true }
    }
    return items.indexOfFirst {
        it.firstOrNull()?.uppercaseChar() == letter
    }
}
