# Audio Asset Notes

The application uses audio files supplied from the following local asset directories:

`D:\misc\资源\音频整理\音效\游戏操作`

`D:\misc\资源\音频整理\音效\互动`

`D:\misc\资源\音频整理\音效\拒绝否定`

| Application resource | Source file | Usage |
| --- | --- | --- |
| `sfx_slide.wav` | `slide.wav` | Tile movement |
| `sfx_merge_*.wav` (48 files) | All 48 WAV files in `互动`, preserving the source filename after the prefix | One random clip per successful move containing a merge |
| `sfx_invalid_*.wav` (32 files) | All 32 WAV files in `拒绝否定`, preserving the source filename after the prefix | One random clip per rejected directional swipe |
| `sfx_click.wav` | `click.wav` in `游戏操作` | Ordinary buttons |
| `sfx_deep_click.wav` | `deepClick.wav` in `游戏操作` | AI auto and pause button |

All successful moves use the slide sound. Moves containing one or more merges also
play one random interaction clip, including during AI automatic play. An invalid
direction plays one random rejection clip. Feedback avoids repeating the previous
clip when alternatives are loaded; a new feedback clip replaces the previous one.
All clips respect the sound toggle and stop when the application goes into the
background. AI hints and game over do not add audio. No background music is used.

The supplied audio files are copied without resynthesis or modification.

The previous Kenney sound resources are no longer included. The source directory
does not contain license metadata, so distribution rights should be confirmed
before publishing the application.

## Tile Number Font

Tile numbers and the bonus tile plus sign use `Numbers-Premium.otf`, supplied
from:

`D:\misc\资源\游戏资源整理\字体\Numbers-Premium.otf`

The source directory does not contain license metadata, so distribution rights
should also be confirmed before publishing the application.
# nneonneo/threes-ai

- Source: https://github.com/nneonneo/threes-ai
- Commit: `c554820ad848a73b7cd34e358c974ab2d85bd792`
- Copyright (c) 2014-2022 Robert Xiao and contributors.
- License: MIT; complete text is in `ai-core/src/main/cpp/upstream/LICENSE` and included in the APK assets.
- The original C++ engine is compiled through JNI. Integration and verification details are in `ai-core/src/main/cpp/UPSTREAM.md`.

