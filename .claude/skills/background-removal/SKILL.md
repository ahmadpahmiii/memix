---
name: background-removal
description: Implement or change Memix background removal — the Segmenter interface, ML Kit Subject Segmentation for photos, MediaPipe person segmentation for video, Apple Vision on iOS, mask caching, the refine brush and "Save as sticker". Use for tickets P2-06, P2-07, P4-13, P7-07 or any cut-out bug.
---

# Background removal

Free in both editors, on-device only. Photos: any prominent subject. Video: **people only in v1**.

| Case | Android | iOS (P7) |
| --- | --- | --- |
| Photo | ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation`, beta, model via Google Play services) | `VNGenerateForegroundInstanceMaskRequest` (iOS 17+) |
| Video, people | MediaPipe Image Segmenter, VIDEO running mode, person model | `VNGeneratePersonSegmentationRequest` + one `VNSequenceRequestHandler` per clip |

Rules:
- Everything sits behind `Segmenter` in `:core:domain`; features never import ML Kit/MediaPipe/Vision.
- ML Kit is beta: check model availability first (`Segmenter.modelState`), show "Preparing…" while Play services downloads it (`prepareModel`, `CutoutProgress`). **No photo fallback in v1** (owner, 5 Nov 2026): if the model is unavailable the user sees "can't remove the background right now" with a retry; P4-13 reconsiders. Features use `RemoveBackgroundUseCase`, which returns a distinct `SegmentationFailure` per case.
- Photo input is decoded at 1024 px long side and the mask comes back at that size as a grayscale PNG file in the project's media folder (never rewritten in place). Pinned: ML Kit subject segmentation 16.0.0-beta1. Debug hook: `memix.segmentCheck` / `memix.segmentPasses` (MemixSegmentCheck log).
- Check current official docs (ML Kit subject segmentation for Android, MediaPipe image_segmenter, Apple Vision) before coding.
- Photo target: < 1.5 s for a 12 MP photo on the owner's phone, the median of 5 warm runs; report the first run (model download and load) apart.
- Video: masks at preview resolution, cached per frame while editing, recomputed at export resolution; show progress; the GL pipeline applies masks as alpha.
- Refine brush: erase/restore strokes saved as a mask-layer PNG at canvas resolution in the project; edge softness = feather radius.
- "Save as sticker" writes a trimmed transparent PNG/WebP into the local sticker list (never uploaded).
- UX per the spec and `design/screens/RemoveBackground.dc.html`: checkerboard, white brush ring, Erase/Restore, brush size and edge softness sliders, success status with duration, Apply as the one blue button.
- Analytics: `bg_remove` with editor, success, duration_ms.
