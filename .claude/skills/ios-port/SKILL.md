---
name: ios-port
description: Phase 7 iOS work for Memix — AVFoundation VideoEngine (AVMutableComposition, custom Metal/Core Image compositor, AVAudioMix, AVAssetWriter, AVPlayerLayer via UIKitView), Vision segmentation, iOS photo encoders and iOS platform services (PHPicker, share sheet, AdMob+UMP+ATT, Firebase). Use only for iOS tickets P7-01…P7-10.
---

# iOS port

Android is the reference implementation: same declarative specs, same results, compared by eye against `docs/qa/references/`.

- Implement in `iosMain` with Kotlin/Native interop to Apple frameworks; keep Swift glue minimal and documented.
- Check Apple's current docs for every API (AVFoundation, Vision, Metal, PhotosUI) before coding.
- Video: build from the shared `CompositionPlan` (`:engine:video` commonMain, same as Android's Media3 builder) rather than re-reading the `Project`. `AVMutableComposition` for tracks, `AVMutableVideoComposition` with a custom compositor (Metal + Core Image) for effects and overlays, `AVAudioMix` for volume/fades, `AVAssetWriter` export as a background task with progress, `AVPlayerLayer` preview hosted in Compose via `UIKitView`.
- Segmentation: `VNGenerateForegroundInstanceMaskRequest` for photos (iOS 17+, the app's floor); `VNGeneratePersonSegmentationRequest` with one `VNSequenceRequestHandler` per clip for video.
- Services: PHPicker, share sheet + direct targets, AdMob + UMP + the ATT prompt, Firebase.
- Budgets as in `mobile-performance`; measure with Instruments.
- A Mac with Xcode is required; say clearly when something wasn't run on a simulator or device.
