# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version corresponds to a
git tag (`vX.Y.Z`) and a GitHub release.

## [Unreleased]

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
