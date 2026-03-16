package com.musicplayer.app.player

enum class RepeatMode {
    OFF,
    ONE,
    ALL;

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}
