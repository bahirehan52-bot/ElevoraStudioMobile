# Elevora Studio Mobile v0.3.0

Android multi-clip editing foundation built with Kotlin, Jetpack Compose and AndroidX Media3.

## Implemented
- Import multiple video clips from Android's document picker.
- ExoPlayer preview for the selected source clip.
- Multi-clip timeline list.
- Reorder clips up/down, delete clips, split selected clip at its midpoint.
- Per-clip trim in/out, speed (0.25x–3x), rotation, color looks, brightness and contrast.
- Global text overlay rendered into exported clips.
- Add a background music source as a looping audio sequence.
- Export assembled video sequence with optional background audio to MP4.
- Save/load project JSON.
- GitHub Actions cloud debug APK build.

## Known limitations
This is not yet a complete CapCut replacement. Preview currently plays only the selected source clip, not the whole composed timeline. Crossfade transitions are not implemented (Media3 Composition currently documents crossfade as unsupported). Animated text/stickers, keyframe animation, subtitle editor, chroma key, reverse playback, advanced color grading, waveform editing, voice recording, multiple independently positioned audio lanes, per-overlay timing and proxy workflow remain future work. Export depends on source codecs and the phone's hardware encoder.

## Cloud APK
Push to main or open Actions, then download artifact named ElevoraStudio-v0.3-debug-APK.

## Local build
JDK 17, Android SDK 35 and Gradle 8.9:
gradle --no-daemon :app:assembleDebug

Minimum Android: 8.0 (API 26). Debug APK is for testing, not Play Store release.
