# Play release path: where P1-15 goes, and when the closed test starts

**Date:** 8 Oct 2026 · **Author:** PM · **Status:** question 1 **Approved: 1B** (owner, 8 Oct 2026). Question 2: ticket P5-08 was added on the owner's answer 11 ("yes personal / start early"). The PM picked the start date (B); the owner can change it with "2A" or "2C".

**Facts used** (Google's help page [answer 14151465](https://support.google.com/googleplay/android-developer/answer/14151465), read through search results on 8 Oct; support.google.com doesn't load from this session. Re-check at P1-15 and when P5-08 starts):
- Personal accounts created after 13 Nov 2023 must run a closed test before the Production track opens. At least 12 testers must have been opted in for at least the last 14 days in a row.
- After that, the owner applies for production access on the Play Console dashboard. The form asks about the app, how it was tested and whether it's ready. Google then reviews it; guides say about a week, which isn't confirmed.
- Only testers who opted in count; sent invites don't. Guides say that if the count drops below 12, the 14 days start over.
- A closed release needs the app in Play Console and a signed upload. To re-check: it probably also needs the store listing and the app content forms (privacy policy URL, ads, content rating, target audience, Data safety).

## 1. Where P1-15 goes

**Problem:** P1-15 (signing, plus CI upload to internal testing) needs the upload key and the Play service account. The owner will make them "soon, when ready to publish". Until then, Phase 1 can't finish all its tickets, and no phase can end with an internal-testing build.

**Suggested fixes:**
- **A. Keep P1-15 in Phase 1.** The P1 gate and PR wait until the credentials exist.
  - Costs: P1 can stay open for weeks after its other tickets are done, and P2 starts on a branch that isn't merged.
  - Gain: Play-installed builds from Phase 1 on.
- **B. Build P1-15 whenever the credentials arrive (recommended).** It's built in the first session after the owner creates them, by **11 Jan 2027** at the latest. Phases close without it:
  - QA uses debug builds on the Mac emulator, plus the release-type build P1-04 adds for the owner's phone (that build needs no Play signing).
  - The "internal-testing build" step at each phase end is skipped until P1-15 lands.
  - Costs: Play-only problems (signing, App Bundle installs, the pre-launch report) show up later, about three months later at worst. P5-08 then runs on real Play builds for two weeks before launch, so they still show up in time. No time lost now.
- **C. Move P1-15 into Phase 5, just before P5-08.**
  - Costs: the key, the first upload, the forms and Google's review of the closed release would all fall in one week. Any snag delays the closed test, which then has no slack.

**Impact:** with B, launch dates don't move, as long as the credentials arrive by 11 Jan 2027. The PRD's "each phase ends with a build in internal testing" is paused until P1-15 lands. Quality is unchanged, because QA was never going to run on Play builds before P6.

## 2. When the closed test starts (P5-08)

**Problem:** the Play account is personal. Before the 7 Mar 2027 launch (P6-06), production access needs a 14-day closed test with at least 12 testers, then Google's review. If testers drop below 12 or the application is refused, the clock starts over, which costs about 3 weeks.

**Suggested fixes:**
- **A. Start on 8 Feb 2027 (the first day of P5).** Production access around 1 Mar. Credentials needed by 25 Jan at the latest.
  - Costs: no slack at all. One restart or refusal moves the launch about 3 weeks (around 28 Mar), and the 5,000 DAU clock moves with it.
  - Gain: testers see a nearly finished app.
- **B. Start around 25 Jan 2027, in P4's last two weeks, on the newest Play build (picked).** Production access around 15 Feb, which the P5 gate can check.
  - Earlier work: credentials by 11 Jan. First versions of the store listing (English), privacy policy page and Play forms move up from P6; P6-03, P6-04 and P6-05 finish them. The owner recruits testers in early January.
  - Costs: 1–2 days of PM and owner work about four weeks earlier than planned. The privacy policy needs a web address by mid-January: the domain (a PRD open item) or a temporary page.
  - Gain: a refused application can run once more and still land around 7–8 Mar.
- **C. Start around 28 Dec 2026, right after P3.** Production access around 18 Jan, with room for two reruns.
  - Costs: credentials and forms by mid-December. The test runs over the holidays, when testers drop off. The build has no pro tools or ads yet, so the "is it ready" answers are weaker.

**Testers (the owner recruits them):**
- 15 to 20 people with Android phones (friends, family, people who make memes), ideally across the five launch languages, so dropouts don't push the count below 12.
- One Google Group as the tester list, so joining takes one link.
- Ask each tester to stay opted in for the full 14 days and to open the app a few times.
- One short feedback form; its answers feed the production-access questions.
- The PM drafts the invite text and the form at P5-08's ready check.

**Impact on the goal:** with B, the 7 Mar 2027 launch holds even after one refusal. Scope doesn't change. Testers' feedback before launch also helps day-1 and day-7 retention.

**Answer with:** 1A / 1B / 1C. Optionally 2A or 2C (2B is in TICKETS now).
