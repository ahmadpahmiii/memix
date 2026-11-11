# Report a sound · P3-10

Part of the Sounds library set; it is the second pane of the details sheet (`P3-03-sound-preview-and-details.md`). Sounds tab and row: `P3-02-sounds-library.md`.

## Job
"This sound shouldn't be here: tell the team in under 20 seconds, without an account." **Done when** (TICKETS): the report appears as a row in Supabase. PRD: "Every sound has a Report option that sends a takedown request to the team." It matters for the legal risk (PRD risks: muted posts, takedowns, store removal).

**The one blue action:** **Send report** (Primary, the only Button on the pane).

## Entry and exit
- **Opens from:** "Report this sound" in the details sheet (P3-03). Only catalog sounds; My sounds have no Report.
- **Same sheet, content swap**, not a second sheet (Sheet README: never stack): the title changes to "Report this sound", a back arrow `arrow_back` (48 dp) appears at the start of the title row, and the content cross-fades over 200 ms.
- **Back arrow, system back, Escape:** return to the details pane; the choice and note are kept for the rest of this sheet visit and cleared when the sheet closes. If the user typed a note, back asks nothing (the note is short; losing it is acceptable) — Assumption.
- **Close, scrim, drag down:** the sheet closes; nothing is sent.
- **Success:** the sheet closes and a toast says thanks; the sound is flagged "reported" on this phone (below).
- **Kept:** the id of every sound this phone has reported (`reported_ids`, on the phone only), so the same sound can't be reported twice from here.

## Layout
Sheet component, `surface`. Top to bottom, `space-4` padding:
1. **Title row:** `arrow_back`, "Report this sound" in `title`; the sheet's close button.
2. **The sound** in `body-strong` `text`, one line with ellipsis (the user knows which sound; the full name is in the details pane).
3. **Prompt** "What's wrong with it?" in `body` `text-secondary`, `space-3` below.
4. **Reasons:** four single-choice rows, each `touch-target` tall at least, the radio on the start side, label in `body` `text`. Selected: the radio filled `selection` white with an `on-primary` dot; unselected: a `text-secondary` ring. The row has no fill (a `surface-raised` row, pressed `hairline`). One choice at a time; none is preselected (a preselected "wrong sound" would produce junk reports). Radio group semantics.
   - Copyright problem
   - Offensive or hateful
   - Broken or wrong sound
   - Something else
5. **Details field** (`space-4` below): label "Details (optional)", a multi-line field in the P1-10 field look (`surface-raised`, `radius-md`, `space-3` padding, at least 3 lines tall), `body` `text`. Limit **480 characters** (grapheme clusters); from 400, the counter "412/480" in `caption` `text-muted`, end-aligned (as P1-14). The limit is 480, not 500, because the stored reason is "{code}: {note}" and the code takes up to 10 of the table's 500.
6. **Info line** (`space-3` below): `label` `text-secondary`: "We can't reply, but the team reads every report." Sets the right expectation for an anonymous form.
7. **Send report:** the Primary Button, full width, pinned at the bottom above the keyboard. The keyboard's action key is "New line" in the note and doesn't send.

The sheet's long-content rule applies: reasons and field scroll between the pinned title and the pinned button, and the keyboard pushes the field into view.

## States
| State | What shows | Copy |
| --- | --- | --- |
| Default | No reason chosen, empty field, Send enabled | strings below |
| Tapped Send with no reason | A one-line message under the reasons in `text`; focus moves to the first reason; announced politely | `sound_report_pick_reason` |
| Sending (> 300 ms) | The button reads "Sending…" and ignores taps; the fields and reasons are disabled-looking | `sound_report_sending` |
| Success | Sheet closes; toast 4 s; the sound is marked reported | `sound_report_thanks` |
| Offline | Inline line above the button; the form is kept; Send stays enabled so the user can try again | `sound_report_offline` |
| Server error or rejected (rate limit) | The same line with the failure wording; the form is kept | `sound_report_failed` |
| Already reported | The pane isn't reachable: the details sheet shows "Reported. Thank you." in place of the Report button | `sound_detail_reported` |
| Long content (5 languages, 200% font) | Reason labels wrap; the sheet scrolls; Send stays pinned; the title row wraps | none |
| Permissions | None. The report carries no account, name, email or device id | none |
| First run | Same | none |

Inline messages use `text` on `surface` (12.4:1) with the label "Couldn't send" first, never color alone. (They don't use `danger` for text because the sheet can sit on `surface-raised` in a later reuse, where `danger` text is 4.3:1.)

## What is sent
`POST reports` (TECHNICAL_DESIGN): `item_kind: "sound"`, `item_id`, `reason: "{code}"` or `"{code}: {note}"` with the code one of `rights`, `offensive`, `broken`, `other` and the note trimmed to 480 characters, `status` `open` by default (RLS). Nothing else: no device id, no app version, no user text beyond the note. The engineer's insert must satisfy the RLS rule (`status = 'open'`, reason ≤ 500).

## Interactions
- **Tap a reason:** selects it (SegmentTick haptic is not used; a plain 120 ms fill change).
- **Tap Send:** validates, sends, shows Sending. A success gives one Confirm haptic with the toast.
- **Tap the note field:** keyboard opens; the sheet scrolls it into view.
- **Back arrow:** returns to details. **Keyboard:** Tab through the arrow, the reasons, the field, Send; arrow keys move within the reasons; Enter on Send sends.

## Motion
Pane swap 200 ms cross-fade, 120 ms fade with reduce motion. Radio and button state changes 120 ms. Nothing else.

## Copy
Budgets as in the other specs. Tone: calm, short, no blame for the reporter.

| Key | English | Budget | Notes |
| --- | --- | --- | --- |
| `sound_report_title` | Report this sound | 28 | Title and, in the details pane, the row label (`sound_detail_report`) |
| `sound_report_prompt` | What's wrong with it? | 32 | |
| `sound_report_reason_rights` | Copyright problem | 32 | |
| `sound_report_reason_offensive` | Offensive or hateful | 32 | |
| `sound_report_reason_broken` | Broken or wrong sound | 32 | Doesn't play, or isn't what its name says |
| `sound_report_reason_other` | Something else | 24 | |
| `sound_report_note_label` | Details (optional) | 28 | |
| `sound_report_count` | {n}/480 | 8 | Numbers only; Hindi numerals are not used (keep Western digits like the app's other counters) |
| `sound_report_info` | We can't reply, but the team reads every report. | 72 | |
| `sound_report_send` | Send report | 24 | Primary |
| `sound_report_sending` | Sending… | 16 | |
| `sound_report_pick_reason` | Pick a reason to send your report. | 48 | Names the next step |
| `sound_report_offline` | You're offline. Connect, then send again. | 56 | |
| `sound_report_failed` | Couldn't send your report. Try again. | 56 | |
| `sound_report_thanks` | Thanks. We'll review this sound. | 44 | Toast |
| `sound_report_back_a11y` | Back to sound details | 32 | The arrow |

## Accessibility
- **Reasons** are a radio group ("Copyright problem, radio button, 1 of 4, not selected"); the group has the prompt as its label. Selecting announces "selected" (the control's own state; nothing extra in the state description).
- **Send** is named "Send report", equal to its label. While sending, "Sending…" is its text, and it is announced politely.
- **Messages** (pick a reason, offline, failed) are polite status messages (SC 4.1.3); the first one moves focus to the control the user must act on (the first reason, or Send), as in the Drafts rename sheet.
- **Order:** back arrow, title, sound, prompt, reasons, field, counter, info line, Send. 48 dp targets on all.
- **Focus:** on entering the pane, focus goes to the title (pane title); on returning to details, to the Report row; after success, the sheet closes and focus returns to the row (Sheet README).
- **200% font:** as the Sheet's long-content rule; Send stays pinned unless the reasons and field need the space, in which case the pinned bar follows `pinned-bar-max-fraction` (P3-03).
- **Contrast:** radio selected white on `surface` 17.2:1; unselected ring `text-secondary` 5.7:1; counter `text-muted` on `surface-raised` 4.6:1.

## Analytics
- `sound_report {sound_id}` once, on a successful insert (not on tap, not on failure).
- **Proposal for the product manager:** add a `reason` parameter holding the code (`rights`, `offensive`, `broken`, `other`; never the note), so the owner can see which kind of report to expect. A one-line PRD table edit; optional, and not fired until the PRD has it.

## QA compares
Screenshots in `docs/ux/reviews/P3-10/`:
1. `details-with-report` (the Report row) and `report-default`.
2. `report-chosen-with-note` (counter showing at 412/480).
3. `report-pick-reason` (inline message), `report-offline`, `report-failed`.
4. `report-hi-200`: Hindi at 200% with the keyboard open, Send reachable.
5. `details-reported`.
Also: a real report inserted from the emulator and seen as a row in the Supabase table (the Done when), noted in the ticket's commit message.

## Questions for the owner
1. **A contact for copyright owners.** A rights holder has no way to write to Memix from this form (the form is anonymous and one-way). **Recommended: none in P3-10;** the P5-07 credits and legal screen carries a "Copyright and takedowns" address and the store listing carries a support email. A takedown form is also a legal question for the lawyer review. Alternative: put the address in this pane's info line now (needs an email address and a decision about who reads it).
2. **After you report, hide the sound for yourself?** **Recommended: no.** The reporter may be wrong, hiding it hides the evidence the team may want the reporter to re-check, and a hidden sound needs an "unhide" screen. The sound stays, marked "Reported. Thank you."
