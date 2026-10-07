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
- ML Kit is beta: check model availability first; show "Preparing…" while Play services downloads it; fall back to the MediaPipe person model and say so if unavailable.
- Check current official docs (ML Kit subject segmentation for Android, MediaPipe image_segmenter, Apple Vision) before coding.
- Photo target: < 1.5 s for a 12 MP photo on the reference phone (measure, put it in the PR).
- Video: masks at preview resolution, cached per frame while editing, recomputed at export resolution; show progress; the GL pipeline applies masks as alpha.
- Refine brush: erase/restore strokes saved as a mask-layer PNG at canvas resolution in the project; edge softness = feather radius.
- "Save as sticker" writes a trimmed transparent PNG/WebP into the local sticker list (never uploaded).
- UX per the spec and `design/screens/RemoveBackground.dc.html`: checkerboard, white brush ring, Erase/Restore, brush size and edge softness sliders, success status with duration, Apply as the one blue button.
- Analytics: `bg_remove` with editor, success, duration_ms.
