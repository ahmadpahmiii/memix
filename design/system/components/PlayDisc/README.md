# PlayDisc

The big play button centered on a video that isn't playing. First built in P1-12 (the Exported screen's result); later the same on the Sounds and template previews if they play video.

- **Provide:** nothing but a modifier. It only draws; the tap belongs to the video it sits on, so the whole picture is one target (a 56 dp disc alone would be a poor target on a 1080 × 1920 picture).
- **Look:** a `radius-full` disc of `play-disc-size` (56 dp) in `primary`, the `play` icon (24 dp) centered in `on-primary` (7.9:1). No outline, no shadow, no gradient. It is the one blue thing on the Exported screen next to Done; both can show because the disc is content-level (a play control on a picture), not the screen's main action.
- **Motion:** fades in and out over `duration-press` when playback stops or starts; with reduce motion it just changes.
- **Pressed:** no separate state: the video's tap target has no indication, the disc going away (or the pause) is the feedback.
- **Accessibility:** the disc is hidden from screen readers (`contentDescription = null`). The video container is one button named "Play your video", or "Pause your video" while playing, so the name always says what a tap does (SC 4.1.2). The disc's contrast over arbitrary footage can't be guaranteed (SC 1.4.11 can't apply to content), which is why the name carries the meaning.
- **Don't:** use it for an icon control (use IconButton), place a label under it, or show it while the video plays.
