# Squish

Share a video to it (or open it and pick one yourself) → comes back under a size
ceiling → share sheet reopens so you can fire it at Discord/Signal/WhatsApp.

Hardware encode via Media3 Transformer. No FFmpeg, no native binaries.

## Setup

1. Android Studio → Open → point at this folder (it's a normal Gradle project, no
   need to create a new one).
2. Sync, run. The app has a launcher icon now, or share a video to it from your
   gallery/files app — either way lands on the same decision screen.

## Getting a build without Android Studio

`.github/workflows/release.yml` builds an APK in CI and always publishes it as a
GitHub Release:

- **Push a tag** like `v1.0.0` → release under that tag.
- **Or run it manually** from the Actions tab (Actions → Build & Release APK →
  Run workflow) any time you want a fresh build without tagging — it publishes as
  `build-<run number>`, marked prerelease so it doesn't look like a real version.

Without signing secrets configured (see below) it falls back to a debug build
(debug-signed, unoptimized) — fine for sideloading onto your own device, but every
debug build is signed with a throwaway key, so installing a newer one over an older
one can fail with a signature mismatch depending on your device. Signing fixes that.

## Signing release builds

`app/build.gradle.kts` reads the release signing config entirely from environment
variables — nothing is hardcoded or committed:

| Env var | What it is |
|---|---|
| `SQUISH_KEYSTORE_PATH` | Local filesystem path to the `.jks` keystore file |
| `SQUISH_KEYSTORE_PASSWORD` | Keystore password |
| `SQUISH_KEY_ALIAS` | Key alias inside the keystore (`squish` if you used the setup below) |
| `SQUISH_KEY_PASSWORD` | Key password (same as the keystore password for a PKCS12 keystore, which is what `keytool` makes by default now) |

If they're not set, `assembleRelease` still works — it just produces an **unsigned**
APK (`app-release-unsigned.apk`), which won't install on a device as-is.

**Building a signed release locally:**
```
export SQUISH_KEYSTORE_PATH=/path/to/squish-release.jks
export SQUISH_KEYSTORE_PASSWORD=...
export SQUISH_KEY_ALIAS=squish
export SQUISH_KEY_PASSWORD=...
./gradlew assembleRelease
```

**Building a signed release in CI:** add these four **repository secrets**
(Settings → Secrets and variables → Actions → New repository secret) — the workflow
picks them up automatically once they exist, no other change needed:

| Secret name | Value |
|---|---|
| `SQUISH_KEYSTORE_BASE64` | The keystore file, base64-encoded (`base64 -w0 squish-release.jks`) |
| `SQUISH_KEYSTORE_PASSWORD` | Keystore password |
| `SQUISH_KEY_ALIAS` | `squish` |
| `SQUISH_KEY_PASSWORD` | Key password |

**Generating a keystore**, if you don't have one:
```
keytool -genkeypair -v -keystore squish-release.jks -alias squish \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep this file and its passwords somewhere permanent and backed up — it's the
identity of the app. Losing it means every future release has to switch to a new
key, and anyone with an old build can't seamlessly update to a new one signed by a
different key.

## Screens

0. **Home** — shown when you tap the app icon directly instead of sharing into it.
   Just a **Pick a video** button (`ActivityResultContracts.OpenDocument`, `video/*`)
   that feeds into the same flow below. Also the fallback screen if a restored source
   URI from a previous session can no longer be read.
1. **Decision** — thumbnail, filename, duration/size, a resolution row (**Auto** /
   **Original** / **1080p** / **720p** / **480p**), and two cards ("Keep audio" /
   "Mute") each showing the real predicted resolution and estimated output size for
   that choice, computed by the same `CompressionPlanner.plan()` the exporter uses.
   A target-size chip row (8/16/25/50/100 MB) recomputes both live. Sources already
   under the ceiling skip straight past this screen.
2. **Progress** — dimmed thumbnail, determinate progress bar (falls back to
   indeterminate if `Transformer.getProgress()` reports `PROGRESS_STATE_UNAVAILABLE`),
   elapsed time + ETA, and a working Cancel.
3. **Result** — size before/after with percent saved, final resolution/audio state,
   auto-launches the share sheet, plus **Save to gallery**, **Share again**, and
   **Try again**.
4. **Error** — the `ExportException` code/message, **Retry at lower quality** (drops
   one rung on the resolution ladder), **Share original** as an escape hatch, and
   **Mute and retry** when the failure happened on an audio-encoding attempt.

All of this lives in Jetpack Compose + Material 3 (dynamic color on Android 12+, dark
theme follows the system, edge-to-edge). `MainActivity` is now just a host: all state
and the `Transformer` lifecycle live in `CompressionViewModel`, driven by a
`StateFlow<UiState>` sealed interface (`Home`, `Loading`, `Deciding`, `Compressing`,
`Done`, `Failed`). Rotating mid-export no longer restarts anything —
`android:configChanges` is gone from the manifest because there's nothing left for it
to work around.

## Manual resolution override

`ResolutionChoice` (`data/ResolutionChoice.kt`) sits next to the audio choice on the
decision screen:

| Choice | What it pins the output height to |
|---|---|
| **Auto** | Original behavior — highest ladder rung that clears `MIN_BPP` at the computed bitrate. |
| **Original** | The source's own height (portrait-corrected), i.e. don't downscale at all. |
| **1080p / 720p / 480p** | That height exactly. |

Every non-Auto choice is capped to the source's own height in `CompressionPlanner
.resolveHeight()` so picking "1080p" on a 720p source doesn't upscale it — the plan
just reports 720p and you can see that on the card before committing. The trade-off is
yours: forcing a higher resolution at the same byte budget means a lower bits-per-pixel
ratio (softer video), which is exactly the ladder logic Auto exists to avoid — but
sometimes you'd rather keep the resolution and accept that than get auto-downscaled.

## Save to gallery

The result screen's **Save to gallery** button copies the output into the device's
`Movies/Squish` collection via `MediaStoreSaver` (`data/MediaStoreSaver.kt`):

- **Android 10+ (API 29+)**: inserts through `MediaStore` with `RELATIVE_PATH` and
  `IS_PENDING`, scoped-storage style — no permission needed.
- **Android 6-9 (API 23-28)**: writes directly to the public Movies directory, then
  `MediaScannerConnection.scanFile()` so it shows up in the gallery. This path needs
  `WRITE_EXTERNAL_STORAGE` (declared with `maxSdkVersion="28"` in the manifest, so it's
  a no-op grant on newer OSes); the button requests it at runtime on those API levels
  before calling into the ViewModel.

## Launcher icon

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` is an adaptive icon (background +
foreground layers); legacy raster fallbacks live in `mipmap-{m,h,xh,xxh,xxxh}dpi/` for
API < 26. Regenerate them from `gen_icon.py`-style Pillow output if you want a
different look — there's no vector source of truth checked in, just the rasters.

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
