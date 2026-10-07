# Toggle

An on/off switch for settings that apply right away: watermark preview, mute original audio, keep audio when changing speed.

- **Provide:** a label (sentence case, says what turns on), the current value and the change action. The whole row is the touch target, not just the switch.
- **Off:** `hairline` track with a 16px `text-secondary` thumb at the start. The thumb, not the track, carries the state: 4.7:1 against the track, 7:1 against `surface`.
- **On:** `primary` track with a 22px `on-primary` thumb at the end (7.9:1). On is the only blue in the row.
- **Size:** track 52 × 32px inside a row at least `touch-target` tall; thumb travels in 120 ms, a fade under reduce-motion.
- **Pressed:** the thumb grows to 26px while held. Nothing else moves.
- **Don't:** use a toggle for something that needs a confirm step or an Apply button; use a checkbox look; add an outline around the track.
