# ProgressBar

Determinate progress for work Memix can measure: importing media (P1-02), and later export and background removal.

- **Look:** a `rail-height` rail in `hairline`, a `primary` fill, `radius-full` ends, full width of its container. No outline, no glow, no gradient.
- **Provide:** a value from 0 to 1, and a count or percent shown in text next to it. The bar is never the only signal.
- **Motion:** the fill moves to each new value over `duration-press`, linear; at most 10 updates a second. Reduce motion: it jumps.
- **Determinate only.** No looping indeterminate bar: nothing in Memix moves on its own (MOTION 2). When the total is unknown, count items or show the amount done in text.
- **Screen readers:** a progress range from 0 to 1 plus a state description ("40 percent"). Not a live region itself; announce milestones from the text next to it.
- **Contrast:** the fill is 7.3:1 on `surface`, 5.9:1 on `surface-raised` and 7.9:1 on `canvas`, and 4.9:1 against its `hairline` rail. Every one is above the 3:1 needed for UI parts.
