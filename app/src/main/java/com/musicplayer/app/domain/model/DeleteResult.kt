package com.musicplayer.app.domain.model

import android.content.IntentSender

sealed class DeleteResult {
    object Deleted : DeleteResult()
    data class RequiresConfirmation(
        val intentSender: IntentSender,
        val song: Song
    ) : DeleteResult()
    object Failed : DeleteResult()
}