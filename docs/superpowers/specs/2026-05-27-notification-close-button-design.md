# Notification Close Button — Design

**Date**: 2026-05-27
**Scope**: Make the existing `Close` `CommandButton` actually appear in the media notification card on the pull-down notification shade.

## Problem

`PlaybackService.kt:100-108` defines a `Close` `CommandButton` bound to a custom session command (`COMMAND_STOP_SERVICE`) and registers it via `MediaSession.Builder.setCustomLayout(listOf(closeButton))`. The handler in `onCustomCommand` already stops the player, clears the queue, and calls `stopSelf()`. Yet no close button surfaces in the system notification.

## Root cause

Custom session commands are not granted to controllers by default. The notification controller (which renders the media card in the shade) connects to the session and receives only `MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS` — an effectively empty set for a non-library session. Because `COMMAND_STOP_SERVICE` is not in that set, Media3 filters the `Close` `CommandButton` out of the rendered notification entirely. The button is defined but invisible.

This is independent of slot configuration (`setSlots`, which would be the right tool but only exists in Media3 1.4+; this project is pinned to 1.3.1).

## Fix

Override `MediaSession.Callback.onConnect` in `PlaybackService.kt` to grant `COMMAND_STOP_SERVICE` to every connecting controller. Once the controller's available session commands include this command, Media3's `DefaultMediaNotificationProvider` will include the `Close` button alongside play/pause/skip when rendering the notification.

```kotlin
override fun onConnect(
    session: MediaSession,
    controller: MediaSession.ControllerInfo
): MediaSession.ConnectionResult {
    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
        .buildUpon()
        .add(SessionCommand(COMMAND_STOP_SERVICE, Bundle.EMPTY))
        .build()
    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(sessionCommands)
        .build()
}
```

No other changes needed — the button definition, custom layout registration, and `onCustomCommand` handler are already correct.

## Behavior on tap

Tap Close → `onCustomCommand` runs `player.stop()` + `player.clearMediaItems()` + `stopSelf()`. Notification dismisses, foreground service ends.

**Snapshot is intentionally preserved** (v1.1.10's `saveLastSong` empty-queue guard prevents the clear from wiping it). On next app launch, the MiniPlayer rehydrates with the last-played song as a "resume" affordance — matches Spotify/Poweramp behavior.

## Out of scope

- Custom `MediaNotification.Provider` subclass — not needed once the command is granted.
- `deleteIntent` for swipe-dismiss parity.
- Clearing the snapshot on Close (would require a "user-initiated stop" signal to bypass the v1.1.10 empty-queue guard).
- Media3 dependency upgrade.

## Verification

1. `./gradlew assembleDebug` compiles cleanly.
2. Install debug APK on device.
3. Start playback → pull down notification shade.
4. Close (×) button visible in the media card.
5. Tap Close → notification dismisses, in-app MiniPlayer disappears.
6. Relaunch app → MiniPlayer rehydrates with the last song (snapshot preserved).

## Files touched

- `app/src/main/java/com/musicplayer/app/player/service/PlaybackService.kt` — add `onConnect` override inside the existing `MediaSession.Callback`.
