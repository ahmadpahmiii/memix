---
name: ux-research
description: How the Memix UX designer researches — competitor teardowns (CapCut, VN, InShot, meme apps), store-review mining, platform guidelines (Material 3, Apple HIG), accessibility (WCAG 2.2), heuristic evaluation of Memix flows, tap-count analysis, and short usability-test scripts for the owner. Use before each phase's UI work, when a UX problem is reported, or before a design change.
---

# UX research

You can't interview users, so research is desk research plus heuristic evaluation, and real-user tests are scripts the owner runs. Every claim gets a source; label evidence strength: **Evidence** (data, studies, many consistent reviews) · **Guidance** (platform or expert guidelines) · **Assumption** (our reasoning; test it).

## Methods

1. **Competitor teardown.** CapCut, VN, InShot for editing; Imgflip, Mematic and similar for meme formats. Use official store listings, help centers, release notes and public screenshots. Record: how many taps for each Memix job (PRD table), where sounds live, how selection/trim/undo work, what's paywalled.
2. **Store-review mining.** Read recent 1–3★ and 5★ reviews (Play Store / App Store pages) of 3–5 competitors; cluster pain points and delights; quote 2–3 representative lines per cluster with links.
3. **Guidelines.** Material 3 (Android), Apple HIG (iOS), WCAG 2.2 AA, Android accessibility docs; NN/g articles for interaction patterns.
4. **Heuristic evaluation** of our boards/builds: Nielsen's 10 + Memix checks: any sound ≤ 2 taps from the timeline; basic meme < 60 s; one-handed reach for primary actions; undo everywhere; no dead ends; errors say what to do; five-language text fits; reduce-motion respected.
5. **Tap-count / flow analysis** for each PRD job: steps, decisions, waits; target ≤ the best competitor.
6. **Usability test script** (when a decision needs real users): 5 participants, 3–5 tasks from the PRD jobs, what to observe, success criteria, a notes template. The owner runs it on a phase build; you analyze the notes.

## Output

`docs/ux/research/<yyyy-mm-dd>-<topic>.md`:
1. Question and why now (phase/ticket)
2. Sources (links)
3. Findings (each with evidence strength)
4. Implications for Memix
5. Recommendations: design changes (you can make them) vs scope changes (proposal for the product manager, with ticket refs)
6. Open questions for the owner

Keep it short: the decision and its evidence, not an essay.
