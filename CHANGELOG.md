# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version corresponds to a
git tag (`vX.Y.Z`) and a GitHub release.

## [1.1.16] - 2026-10-02

### Added
- Skip-backward button on Now Playing, mirroring skip-forward so Play stays centered. Uses the same configurable jump length (15s / 30s / 1 min).

## [1.1.15] - 2026-09-26

### Added
- Skip-forward button on Now Playing; jump length (15s / 30s / 1 min, default 30s) selectable in Settings > Audio.

## [1.1.14] - 2026-09-15

### Fixed
- Mini player and bottom nav disappearing after tapping the media notification: the Now Playing
  open animation cancelled itself and left the overlay parked off-screen.
- Notification Close reset the current song to the first queue entry.
- Queue-tab removal left the song in ExoPlayer; "Play Next" appended to the end of the queue;
  queue operations after a notification Close acted on an empty player.
- Unplayable file caused an infinite re-prepare loop; now one retry per item, then skip.
- Pause from the notification or headset was not recorded as a user pause, so Bluetooth and
  foreground auto-resume restarted it; a finished queue no longer auto-resumes.
- Playback resumes from the persisted queue when the system starts the service after process
  death (`onPlaybackResumption`); restore/connect race and sticky headset broadcast handled.
- Crash when the equalizer restore touched a released effect; band levels clamped to hardware.
- Navigation crash on artist/genre/composer names containing `/` (persisted route also crashed
  every cold start); routes are now encoded and guarded.
- First launch scanned before the storage permission and wiped the cache; scan now waits and
  rescans on grant. Nested custom folders produced duplicate song ids (LazyColumn crash).
- Playlist positions collided after removal; deletes ran on the main thread and left cached rows;
  multi-disc track numbers on Android 11+; missing write permission on Android 9 and below.
- Settings shortcuts scrolled to the wrong rows; About showed "Version 1.0"; rotation
  re-navigated to the last source screen; Back could never exit from Library.
- Search history duplicates; Genres search filter and folder results did nothing; folder
  hierarchy was a dead end; Song Info size was blank; swipe thresholds now density independent.

### Changed
- Equalizer Limiter now drives a real `DynamicsProcessing` limiter stage (Android 9+).
- Show Waveform setting is wired to Now Playing; playlist song removal asks for confirmation.
- Removed settings controls that had no implementation (Dark Mode, Gapless, Crossfade,
  Album Art, Lock Screen Controls, Export/Import).

### Docs
- Architecture diagram redrawn with [diagram-design](https://github.com/cathrynlavery/diagram-design):
  `docs/architecture.html` is the editable source, `docs/architecture.svg` its export. The draw.io
  and Mermaid sources and the `scripts/render_drawio.py` renderer are gone.

### Removed
- Unused `media3-ui` dependency — playback runs on `media3-session` + `media3-exoplayer`
  with a custom Compose UI; no `PlayerView` is used.
- Dead code: `Song.formatBadge`, `PlaybackController.pause()` (inlined by `togglePlayPause`),
  unread `selectedGenre`/`selectedYear` flows, the `PowerampSurfaceContainer` color, the empty
  `data/model/` package, and the stale `accompanist`/`material3` version pins.

## [1.1.13] - 2026-06-10

### Fixed
- Playback state-leak where a song's elapsed position transferred onto the next track when
  swiping the player bar; position now resets on every track change.
- In-app currently-playing bar (MiniPlayer) disappearing when changing song or interacting via
  the notification.
- Equalizer, snapshot recency, and intent re-trigger bugs surfaced by a state-machine audit.

## [1.1.12] - 2026-05-30

### Fixed
- Bluetooth resume, playback state persistence, and equalizer application after an audio-session
  change.

## [1.1.11] - 2026-05-28

### Added
- Close button on the media notification.

## [1.1.10] - 2026-05-24

### Fixed
- MiniPlayer loss when switching Bluetooth output.

### Docs
- Documented Bluetooth-toggle recovery and snapshot-on-pause behavior.

## [1.1.9] - 2026-05-08

### Fixed
- Recovery from player errors and the ExoPlayer `IDLE` state.

### Changed
- Ignore IDE config and build artifacts.

## [1.1.8] - 2026-05-04

### Fixed
- Recovery from `MediaController` failures and empty playback state.

## [1.1.7] - 2026-05-01

### Added
- Resume-on-foreground playback and Bluetooth disconnect resilience.

### Docs
- Expanded project documentation.

## [1.1.6] - 2026-04-26

### Added
- Library cache: scanned songs persisted to Room for instant startup.

### Fixed
- Keep Screen On toggle now wired to the activity window.

## [1.1.5] - 2026-04-25

### Fixed
- Filename sort uses natural alphanumeric order.

## [1.1.4] - 2026-04-21

### Added
- Resume playback when seeking while paused.

### Fixed
- Prevent swipe transition past queue edges in Now Playing.

## [1.1.3] - 2026-04-14

### Fixed
- Bluetooth disconnect resilience and auto-scroll to the current song.

## [1.1.2] - 2026-04-14

### Fixed
- Stabilization release following the v1.1 refactor.

## [1.1] - 2026-04-13

### Fixed
- Race conditions, Bluetooth filtering, and null-safety bugs across the playback and data layers.

## [1.0.5] - 2026-03-26

### Added
- Folder continuation and a signed release APK.

### Fixed
- MiniPlayer disappearing on app resume.

[1.1.13]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.13
[1.1.12]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.12
[1.1.11]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.11
[1.1.10]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.10
[1.1.9]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.9
[1.1.8]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.8
[1.1.7]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.7
[1.1.6]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.6
[1.1.5]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.5
[1.1.4]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.4
[1.1.3]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.3
[1.1.2]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1.2
[1.1]: https://github.com/michel-ch/musicplayer/releases/tag/v1.1
[1.0.5]: https://github.com/michel-ch/musicplayer/releases/tag/v1.0.5
