# Export (P1-12): a Share button now, and the first permission Memix asks for

**Date:** 20 Oct 2026 · **Author:** PM (from the designer's P1-12 draft spec, 90c8b07 → Proposals and questions for the PM, 2 and 3) · **Status:** Proposed

P1-12's spec can't be finished until these are answered. Latest useful answer: about 24 Oct, when P1-11 should be built and P1-12 is next.

## Question 1: a Share button before the full share row

**Problem:** P1-12 saves the video to the gallery. The share row (TikTok, Instagram, WhatsApp, YouTube Shorts, Save, More) is P5-05, which starts about 8 Feb 2027. The closed test with 15 to 20 outside testers starts about 25 Jan 2027, two weeks before that. For those 14 days, testers can only post by leaving Memix and finding the video in the gallery app. Much of their feedback will be about that, not about making memes.

**Considerations and suggested fixes:**
- **A. One "Share" button on the Exported screen now (recommended).** It opens Android's own share sheet with the video attached. P5-05 later replaces it with the full row.
  - Time: about half a day inside P1-12, which is already the top of L.
  - Money: none.
  - Risk: low. The system share sheet is standard. It fires `share_target` with `target: "more"`, the same value P5-05's More button will use.
  - User impact: testers, and you at phase-end QA, post straight from Memix.
- **B. Wait for P5-05.**
  - Time and money: none now.
  - User impact: the closed test runs without a way to share from Memix, and testers judge that.
- **C. Build the whole share row earlier,** at the end of Phase 4, before the closed test.
  - Time: the same 2 to 3 days as P5-05, just moved.
  - Risk: it moves work into Phase 4, the fullest phase, just before the closed test starts.

**Recommendation: A.** Half a day removes the most obvious gap testers will hit.

## Question 2: asking for notification permission

**Problem:** on Android 13 and newer, an app needs the user's OK to show notifications. Without it, an export still finishes when the user leaves Memix, but the progress and "Your meme is saved" don't show in the notification shade. They show only in Android's list of running apps, which few people open. Memix asks for no permissions today (the gallery picker needs none), so this would be the first.

**Considerations and suggested fixes:**
- **A. Ask once, the first time the user taps Export video (recommended).** Android's own dialog, no extra Memix screen. The export starts at once behind the dialog, whatever the answer.
  - Time: an hour or two inside P1-12.
  - Money: none.
  - Risk: a dialog at the user's first export, which is part of the first impression. Android stops showing it after two refusals, so we get one or two chances.
  - User impact: it's asked exactly when its value is obvious (a long export the user may leave). People who allow see progress and the "saved" message when they leave the app. Android 10 to 12 users never see a dialog.
- **B. Never ask in v1.**
  - Time and money: none.
  - User impact: on Android 13 and newer, people who leave Memix during an export see nothing until they come back. Long exports (2K, 4K, long memes) feel lost.
- **C. Ask only if the user wants it:** a small "Notify me when it's done" button on the Exporting screen opens the dialog.
  - Time: about half a day (button, states, copy in five languages).
  - Risk: none to the first export, but far fewer people will see or allow it.
  - User impact: no interruption; most people won't get the notification.

**Recommendation: A.** Leaving the app during an export is a normal case, and the export doesn't wait for the answer.

## Impact (both)

No date moves. P1-12 grows by at most half a day plus an hour or two. It's already L, so I watch its size: if it runs past two build sessions, I write a split proposal. The 5,000 DAU goal: sharing straight from the app is the end of every meme, and exports per daily user (target 1.5) depends on finishing and posting. Quality is unchanged.

**If approved:** the PM records it in `docs/TICKETS.md` (a note on P1-12, and for Share a note on P5-05 that it replaces P1-12's button) and in `decision-log.md`. The designer finishes the P1-12 spec. The PRD stays as it is: the share row and the background export are already in it.
