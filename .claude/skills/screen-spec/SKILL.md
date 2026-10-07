---
name: screen-spec
description: Template and rules for the screen spec the Memix UX designer writes before any UI ticket is built — purpose, layout vs board, components and tokens, every state, interactions, motion, copy with character budgets, accessibility, analytics and what QA compares. Use when a UI ticket is next or a screen changes.
---

# Screen spec

File: `docs/ux/specs/<TICKET-ID>-<screen>.md`. Write only what the ticket needs; link the board instead of re-describing it.

```markdown
# <Screen> · <TICKET-ID>

## Job
Which PRD job this serves; the one main action (the blue button).

## Entry and exit
How users arrive, where every exit goes (back, close, success), what is kept.

## Layout
Board: design/screens/<File>.dc.html. Differences from the board: …
Components: <DS component> (tokens: surface-raised, radius-lg, …)

## States
| State | What shows | Copy |
| Default | | |
| Empty | | |
| Loading (> 300 ms) | | |
| Error / offline | | |
| Permission denied / limited | | |
| Long content (long names, 5 languages) | | |
| First run | | |

## Interactions
Taps, long-press, drag, gestures, haptics; what's selected (white) vs primary (blue).

## Motion
Durations (120/200/320 ms), what moves; reduce-motion fallback (120 ms fade).

## Copy
English source text, character budget per string, notes for translators (tone, placeholders). Watch Hindi/Portuguese length and Devanagari line height.

## Accessibility
Labels for icon buttons, focus/TalkBack order, touch targets ≥ 48 dp, contrast notes, 200% font scale behavior.

## Analytics
PRD events fired here, with parameters.

## QA compares
The 3–6 states QA must screenshot and compare at phase end.
```

Rules: tokens only (never raw hex or dp the system doesn't have; propose a token if needed); one blue action per screen; selection white; no outlines, hard shadows or gradients; v1 yellow/pink never.
