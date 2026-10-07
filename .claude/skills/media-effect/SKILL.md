---
name: media-effect
description: Add or change a Memix video/photo effect, filter, transition, text animation or speed preset — declarative spec in :core:model, registry entry, Android Media3/GL implementation, photo parity, iOS mapping or marked stub, reference render and UI listing. Use for any ticket that adds visual or audio processing.
---

# Add a media effect

1. **Spec.** In `:core:model`, a serializable spec: stable `id` (e.g. `effect.zoom_punch`), typed params with defaults and ranges, a name string key. Data only.
2. **Registry.** Register it in the effect catalog (category, icon, default duration, intensity range) so UI lists come from data.
3. **Android.** Map the spec to Media3 effects in `:engine:video` androidMain (and `:engine:photo` if it applies to photos): built-in effects where they exist, otherwise a GLSL program; matrix transforms for motion (zoom punch, shake). Animate params over the item's time range in µs. Check current Media3 effect APIs first.
4. **Photo parity.** Filters share ids and LUTs/params between photo and video so looks match.
5. **iOS.** iosMain mapping (Core Image / Metal) or a clearly marked `TODO(P7-04)` stub that reports `UnsupportedEffect`, never a silent no-op.
6. **Audio effects** (pitch, echo, bass boost, voice presets): Media3 audio processors on Android, `AVAudioMix` + processing on iOS.
7. **Reference render.** Render the fixed sample project with the effect and save a frame (or short strip) to `docs/qa/references/<effect-id>.png`. QA and the iOS port compare against it by eye.
8. **Performance.** Preview still ≥ 30 fps at 1080p with 6 tracks on the reference phone; put the number in the PR.
9. **UI.** Listed in its tool with a thumbnail and an intensity slider; on the timeline it's an `EFFECT` item in `track-effect` violet.
