# Squish

Share a video to it → comes back under a size ceiling → share sheet reopens so you can
fire it at Discord/Signal/WhatsApp.

Hardware encode via Media3 Transformer. No FFmpeg, no native binaries.

## Setup

1. Android Studio → Open → point at this folder (it's a normal Gradle project, no
   need to create a new one).
2. Sync, run. The app has no launcher icon — it only shows up when you hit
   Share on a video.

## Screens

1. **Decision** — thumbnail, filename, duration/size, and two cards ("Keep audio" /
   "Mute") each showing the real predicted resolution and estimated output size for
   that choice, computed by the same `CompressionPlanner.plan()` the exporter uses.
   A target-size chip row (8/16/25/50/100 MB) recomputes both live. Sources already
   under the ceiling skip straight past this screen.
2. **Progress** — dimmed thumbnail, determinate progress bar (falls back to
   indeterminate if `Transformer.getProgress()` reports `PROGRESS_STATE_UNAVAILABLE`),
   elapsed time + ETA, and a working Cancel.
3. **Result** — size before/after with percent saved, final resolution/audio state,
   auto-launches the share sheet, plus **Share again** and **Try again**.
4. **Error** — the `ExportException` code/message, **Retry at lower quality** (drops
   one rung on the resolution ladder), **Share original** as an escape hatch, and
   **Mute and retry** when the failure happened on an audio-encoding attempt.

All of this lives in Jetpack Compose + Material 3 (dynamic color on Android 12+, dark
theme follows the system, edge-to-edge). `MainActivity` is now just a host: all state
and the `Transformer` lifecycle live in `CompressionViewModel`, driven by a
`StateFlow<UiState>` sealed interface (`NoInput`, `Loading`, `Deciding`, `Compressing`,
`Done`, `Failed`). Rotating mid-export no longer restarts anything —
`android:configChanges` is gone from the manifest because there's nothing left for it
to work around.

## The audio decision

Before compression starts, you choose to keep or mute the audio track:

| Choice | Implementation |
|---|---|
| **Mute** | `EditedMediaItem.setRemoveAudio(true)` |
| **Keep, compressed** | `setRemoveAudio(false)` + `DefaultEncoderFactory.setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(...))` |
| **Keep, fallback** | `setRemoveAudio(false)` with no audio encoder settings, if the compressed attempt fails |

Audio bitrate defaults to **64 kbps for mono, 96 kbps for stereo** (channel count read
via `MediaExtractor`). Setting `setRequestedAudioEncoderSettings` isn't optional
decoration — it's what makes `DefaultEncoderFactory.audioNeedsEncoding()` return `true`
and forces an actual re-encode. Without it, Transformer transmuxes the original AAC
stream through untouched at whatever bitrate the source used (verify with `ffprobe`
on the output if you change this code — a kept-audio track sitting at ~128k when you
asked for 64k means the transmux path was taken again).

If the compressed-audio export throws, `CompressionViewModel` retries once with no
audio encoder settings (device default), still keeping the track — some devices
reject specific AAC bitrate/profile combinations. If that retry also fails, the app
surfaces an explicit **Retry at lower quality** / **Mute and retry** choice on the
error screen rather than silently dropping audio the user asked to keep.

## How the sizing works (updated budget formula)

```
totalBps        = (targetBytes × 8) / durationSeconds
overheadBps     = max(totalBps × 2%, 10 kbps)      // MP4 container/moov overhead
audioBps        = 0 if muted, else 64k/96k chosen above
videoBps        = totalBps − audioBps − overheadBps
```

Then it picks the highest resolution that still clears a bits-per-pixel floor, same as
before. Muting frees the entire audio budget for video — on a long clip that can push
the resolution ladder up a full rung, which is exactly what the two decision cards are
there to show side by side before you commit.

The old `AUDIO_RESERVE_BPS = 128_000` pessimistic reserve is gone — it existed only
because the app had no control over the AAC bitrate. Now that it does, the reserve is
exact instead of worst-case. `REMOVE_AUDIO` (a compile-time constant) is gone too,
replaced by the runtime choice on the decision screen.

Target size is also now a runtime choice (the chip row), not a compile-time constant —
`CompressionPlanner.DEFAULT_TARGET_BYTES` (19 MB) is just the starting value.

## Knobs

`CompressionPlanner` (`app/src/main/java/com/karaza/squish/data/CompressionPlanner.kt`):

| Constant | Does what |
|---|---|
| `DEFAULT_TARGET_BYTES` | Starting ceiling, 19 MB. Changeable at runtime via the chip row. |
| `TARGET_SIZE_OPTIONS_MB` | The chip row's offered sizes. |
| `MONO_AUDIO_BPS` / `STEREO_AUDIO_BPS` | 64k / 96k audio bitrate defaults. |
| `MIN_BPP` | Quality floor before it drops resolution. `0.015` suits screen capture. Push toward `0.03` for camera footage. |
| Resolution ladder (private `LADDER`) | The resolution rungs it's allowed to pick from. |

If the output looks worse than your CRF 28 script did at a similar size, that's
the hardware encoder being less efficient than x264 — expect roughly 20–40% worse
quality at equal bitrate. Dropping a rung on the ladder usually buys it back, which is
exactly what the error screen's "Retry at lower quality" button does.

## If an export fails

The error screen shows the `ExportException` code and message directly. The usual
culprits are exotic source formats (HDR, 10-bit, very high frame rate) or a device
that rejects the requested AAC settings (handled automatically — see above).
`setEnableFallback(true)` stays on for the video encoder settings, same as before.

If it turns out to fail regularly on real videos, that's when bundling
`dev.ffmpegkit-maintained:ffmpeg-kit-min-gpl` as a fallback path earns its
~10 MB (arm64 only) and the GPL obligation. Not before.
