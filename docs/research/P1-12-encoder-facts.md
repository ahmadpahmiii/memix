# P1-12 export: encoder facts (research, 21 Oct 2026)

Answers spec requests 1–4 (docs/ux/specs/P1-12-export.md) and the PM's 20 Oct notes (b)(1)–(4), plus the notification question.
Versions in the repo: Media3 1.11.1, compileSdk 37, targetSdk 36, minSdk 29. androidx.core checked at 1.19.1 (latest release).
Sources read: Media3 1.11.1 `-sources.jar` from dl.google.com (transformer, muxer, common), Media3 RELEASENOTES.md, androidx.core 1.19.1 sources, developer.android.com pages listed per answer.
Not reachable from this session: source.android.com (CDD), support.google.com (Play policy), Wikipedia/ITU. Items that depend on them are marked **unverified**.

---

## 1. Encoder capabilities at the real canvas sizes

**How to ask the phone**
- Media3 `androidx.media3.transformer.EncoderUtil` (`@UnstableApi`): `getSupportedEncoders(mime)` (cached `MediaCodecList(REGULAR_CODECS)`), `isSizeSupported(info, mime, w, h)`, `getSupportedResolution(info, mime, w, h)`, `getSupportedBitrateRange`, `isBitrateModeSupported`, `isHardwareAccelerated`. Source: `EncoderUtil.java` (media3-transformer 1.11.1).
- Frame rate: platform `MediaCodecInfo.VideoCapabilities.areSizeAndRateSupported(w, h, fps)` ("whether a given video size and frameRate combination is supported", level-based, not a speed promise). `getAchievableFrameRatesFor(w, h)` is a vendor speed estimate that "may return null" and "should only be used to compare individual codecs on the device". `getWidthAlignment()` is "a power-of-2 value that video width must be a multiple of". https://developer.android.com/reference/android/media/MediaCodecInfo.VideoCapabilities
- For an offline export, "supported" is what matters; encoding slower than real time is fine. Achievable speed only feeds the time estimate.

**Portrait is checked rotated (important).** By default Transformer encodes portrait frames as landscape and writes a 90° rotation flag in the MP4:
- `Transformer.start()` KDoc: "the output frames' dimensions will be swapped if the output video's height is larger than the width. This is to improve compatibility among different device encoders."
- `VideoEncoderWrapper.getSurfaceInfo()`: `if (requestedWidth < requestedHeight) { swap; outputRotationDegrees = 90 }` ("Encoders commonly support higher maximum widths than maximum heights").
- `Transformer.Builder.setPortraitEncodingEnabled(false)` is the default; enabling it "is likely to result in more failures".
- So `capabilities()` must query **long side as width**: 1920×1080, 2604×1092, 3840×2160, 5162×2160. Bonus: `EncoderUtil.isSizeSupported` cross-checks `CamcorderProfile` only for exactly 1920×1080 and 3840×2160 (Samsung/Huawei/Pixel 6 under-report, b/229825948), so 9:16 1080p and 4K get that workaround; custom sizes don't.

**What the sizes need (codec level limits; numbers from ITU-T H.264 Table A-1 / H.265 Table A.8, unverified in this session)**

| Size (encoded, landscape) | H.264 (16×16 macroblocks) | HEVC (luma samples) |
| --- | --- | --- |
| 1920×1080 (9:16 1080p) | 8,160 MBs: Level 4.0 at 30 fps, 4.2 at 60 | 2.07 M: Level 4 |
| 2604×1092 (1080p 2.39:1 custom) | about 11,250 MBs: Level 5.0 at 30, 5.1 at 60 | 2.84 M: Level 5 |
| 3840×2160 (9:16 4K) | 32,400 MBs: Level 5.1 at 30, 5.2 at 60 | 8.29 M: Level 5 at 30, 5.1 at 60 |
| 5162×2160 (4K custom) | 43,605 MBs: over Level 5.2's 36,864, **needs Level 6** | 11.15 M: over Level 5.x's 8.91 M, **needs Level 6** |

Expect 2160×5162 to be refused by nearly every phone (only encoders that advertise Level 6, typically 8K-recording flagships, can pass). Many H.264 encoders also cap width at 4096, which `getSupportedWidths()` reports. The device's own answer is what counts. These numbers only set expectations.

**What Transformer does when a size isn't supported** (`DefaultEncoderFactory.java`, 1.11.1)
- Default `setEnableFallback(true)` + `setEnableFormatFallback(true)`: it picks the encoder whose closest supported size is nearest in area, aligns the size, then tries reduction factors 0.95, 0.9 … 0.25, then clamps. **It silently exports smaller** and reports it only through `Transformer.Listener.onFallbackApplied(composition, original, fallback)`. The MIME type can also fall back (H.265 to H.264 or the reverse) if the requested one isn't supported by encoder + muxer.
- `setEnableFormatFallback(false)`: exact size only, otherwise `ExportException` with `ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED` (4003), "The requested video encoding format is not supported."
- Gotcha: with exact match, an unaligned size (2604×1092 on an encoder with 16-px alignment) fails. Plan export sizes already aligned to the chosen encoder's `getWidthAlignment()/getHeightAlignment()` (2 on most, 16 on some). Aligning shifts the 2.39:1 aspect by under 1%.

**Recommendation**
- `VideoEngine.capabilities()` (Android): once per process, off the main thread (the first `MediaCodecList` scan isn't free), for each resolution × fps: H.264 hardware encoder at the aligned landscape size → else HEVC → else unavailable. Cache the result. The sheet reads it per option, as the spec says.
- Export with format fallback **off** (no silent downscale). If export still throws 4003, show the failed state and log codec + size.
- Sheet copy for a size that can't be done: keep the spec's "explain on tap, selection unchanged". For a custom shape, the cause is the shape, not "4K" in general, so it needs a variant such as "This phone can't export 4K in this shape." (designer to word it; see question U1).

## 2. Bitrates

- Yes. `DefaultEncoderFactory.Builder().setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bps).setBitrateMode(mode).build())`, then `Transformer.Builder.setEncoderFactory(...)`. An explicit `bitrate` overrides the Format bitrate, CodecDB Lite and the default "Kush gauge" (`w*h*fps*0.07*2`). Source: `DefaultEncoderFactory.resolveVideoEncoderConfiguration`, `VideoEncoderSettings.java`.
- Modes: Media3 accepts only `BITRATE_MODE_VBR` (default) or `BITRATE_MODE_CBR` (`checkArgument` in `setBitrateMode`). CQ and CBR_FD aren't allowed. https://developer.android.com/reference/android/media/MediaCodecInfo.EncoderCapabilities
- Caveats in the code:
  - The bitrate is **clamped** to the encoder's `getBitrateRange()`. Check 4K60's 60 Mbps against it in `capabilities()` and estimate with the clamped value.
  - Encoders that don't support the requested mode are dropped (`filterEncodersByBitrateMode`). Asking for CBR can push the export onto a different, possibly software, encoder, or fail with 4003.
- Accuracy: Android defines `KEY_BIT_RATE` only as "the average bitrate in bits/sec" (https://developer.android.com/reference/android/media/MediaFormat). I found no official tolerance (the CDD's encoder bitrate wording couldn't be fetched, **unverified**). Expectation, to be measured: hardware VBR lands near the target on moving footage but **undershoots on static meme content** (stills, text, held frames), so the file is often smaller than the estimate. ±15% will hold for live footage and fail on the low side for stills.
- **Mode choice: VBR** (Media3 default, every encoder supports it, better quality per byte). Treat the size estimate as an upper bound: it's safe for the free-space check and honest as "up to about 48 MB". Log `ExportResult.averageVideoBitrate`, `fileSizeBytes` (see 4) and the estimate per export, and revisit the wording at phase-end QA. CBR isn't recommended: worse quality, encoder choice changes, and it isn't documented to pad static content up to the target either (**unverified**).

## 3. Foreground service type and limits

| Device | Type to pass | Time limit |
| --- | --- | --- |
| Android 15+ (API 35+) | `mediaProcessing` ("time-consuming operations on media assets, like converting media to different formats"; new in Android 15) | 6 h per 24 h, shared by all of the app's mediaProcessing services and counted separately from dataSync. Reset when the user brings the app to the foreground. At the limit `Service.onTimeout(int, int)` is called, the service stops being foreground, and it has "a few seconds" to `stopSelf()` or the app crashes with `RemoteServiceException: A foreground service of type … did not stop within its timeout`. Starting another one after the budget is used throws `ForegroundServiceStartNotAllowedException`. |
| Android 14 (API 34) | `dataSync` (mediaProcessing doesn't exist yet); types are required when targeting 34+ (`MissingForegroundServiceTypeException`; a missing type permission throws `SecurityException`) | none |
| Android 10–13 (API 29–33) | type optional; pass `dataSync` | none. Android 12+: can't start an FGS while the app is in the background (`ForegroundServiceStartNotAllowedException`), so start it from the Export tap while the screen is visible |

Sources: https://developer.android.com/develop/background-work/services/fgs/service-types , https://developer.android.com/develop/background-work/services/fgs/timeout , https://developer.android.com/about/versions/14/changes/fgs-types-required , https://developer.android.com/develop/background-work/services/fgs/launch , https://developer.android.com/develop/background-work/services/fgs/changes

- Manifest: `android:foregroundServiceType="dataSync|mediaProcessing"` plus `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`. Choose the type at runtime by SDK.
- **Gotcha (verified in source):** `androidx.core` 1.19.1 `ServiceCompat.startForeground` masks the type with `FOREGROUND_SERVICE_TYPE_ALLOWED_SINCE_U`, which **does not include `MEDIA_PROCESSING`**, and has no API 35 branch. Passing mediaProcessing through ServiceCompat ends up as type 0. On API 35+ call `Service.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)` directly; ServiceCompat is fine for dataSync on 29–34.
- Implement `onTimeout(startId, fgsType)` (API 35): cancel the Transformer, delete the pending gallery item, show "Export stopped", `stopSelf()`. A meme export won't come near 6 h; this is for safety.
- Cancel from the notification: a `PendingIntent` action to the service that calls `Transformer.cancel()` on the app thread. The notification-delay rule below also makes the Cancel action worth having.
- Android 16: jobs started alongside an FGS now follow job quotas (fgs/changes), so run the export in the service, not WorkManager. Android 17's background-audio hardening covers audio playback and focus, not encoding, so it doesn't apply here.
- Play Console asks for a declaration for each FGS type when targeting 34+ (**unverified**, support.google.com unreachable). Flag it for `/play-release`.

## 4. Gallery write: straight into a MediaStore pending item?

- `Transformer.start(composition, String path)` takes **only a path** (the ParcelFileDescriptor overload was removed long ago, RELEASENOTES "Remove `Transformer.startTransformation(MediaItem, ParcelFileDescriptor)`"). The default muxer (`DefaultMuxer` → `InAppMp4Muxer.Factory.create(path)`) does `new FileOutputStream(path)`.
- Writing straight in **is possible with a small custom `Muxer.Factory`** (`Transformer.Builder.setMuxerFactory`) that ignores the path and builds `Mp4Muxer.Builder(SeekableMuxerOutput.of(FileOutputStream(pfd.fileDescriptor)))`. Here `pfd = contentResolver.openFileDescriptor(pendingUri, "rw")` and the item was inserted with `IS_PENDING = 1` and `RELATIVE_PATH = Movies/Memix` (both API 29+, which is our minSdk). The muxer needs seek and truncate only, no reads (`FileOutputStreamSeekableMuxerOutput`). Sources: media3-muxer `SeekableMuxerOutput.java`, `Mp4Muxer.java`; https://developer.android.com/training/data-storage/shared/media ("rw" mode, IS_PENDING).
- What the wrapper must copy from `InAppMp4Muxer` (its constructor is private):
  - On `addTrack` for video, add `Mp4OrientationData(format.rotationDegrees)`. Without it, every portrait export plays sideways, because Transformer encodes rotated (see 1).
  - Keep only metadata `MuxerUtil.isMetadataSupported` accepts.
  - Delegate `getSupportedSampleMimeTypes` to `InAppMp4Muxer.Factory`.
- Side effects:
  - `ExportResult.fileSizeBytes` and the in-progress size come from `new File(outputPath).length()` (`MuxerWrapper`), so they'll be unset. Read the size from `pfd.statSize` instead.
  - On failure or cancel, delete the pending uri.
- Don't pass the `DATA` column path as a shortcut. The docs say don't use it to create files, and random writes through direct paths "can be up to twice as slow".
- **Not verified on a device:** that the MediaStore fd seeks correctly on Android 10 (no FUSE) and 11+ (FUSE). This needs a hand check at build time.
- **Decision for the space check:** if the direct write is built (recommended), free space = estimate + 100 MB. If the hand check fails and we fall back to temp file + copy, peak use is 2 × estimate + 100 MB.

## 5. Notification without POST_NOTIFICATIONS (Android 13+)

- The FGS still starts. Apps "don't need to request the POST_NOTIFICATIONS permission in order to launch a foreground service", but must still pass a notification. https://developer.android.com/develop/ui/views/notifications/notification-permission
- If denied: "they still see notices related to foreground services in the Task Manager but don't see them in the notification drawer." **Yes, Task Manager only** (the "Active apps" list behind the button at the bottom of the drawer, Android 13+). The non-FGS notifications in the spec (Done, Failed, Stopped) **aren't shown at all**. The app must show those states itself when reopened.
- Task Manager **Stop** kills the whole app with no callback. Detect it on the next start with `ApplicationExitInfo` `REASON_USER_REQUESTED` and show "Export stopped" in the app. https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping
- Version differences:
  - **Android 10–12L:** no runtime permission; the notification shows.
  - **Android 12+:** FGS notifications may be held back up to 10 s. A notification with an action button (our Cancel) is shown immediately. https://developer.android.com/about/versions/12/behavior-changes-all
  - **Android 13+** (we target 36): new installs start with notifications off, and the app decides when to ask.
  - **Android 14+:** even with permission, users can swipe away the ongoing FGS notification (except when locked or via "Clear all"). The export keeps running, so the Exporting screen must not rely on it. https://developer.android.com/about/versions/14/behavior-changes-all

## Questions to raise
- U1 (designer): explain-on-tap copy when the custom shape, not the resolution, is the limit (2160×5162 needs Level 6 almost nowhere).
- P1 (PM/designer): the size estimate is reliable as an upper bound, not ±15% for stills. Show "up to about X MB", or keep "about" and accept smaller files?
