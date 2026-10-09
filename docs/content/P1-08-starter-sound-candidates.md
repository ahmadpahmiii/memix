# P1-08 starter sounds: candidate list for the owner's license approval

Prepared by the backend engineer on 8 Oct 2026. The owner approves each sound before it ships (TICKETS.md: "P1-08 waits until the owner approves each starter-sound license (CC0 or equivalent)"). This is not legal advice; the PRD still recommends an IP-lawyer review before launch.


> **Owner decision, 8 Oct 2026:** approved 1–3 and 5–11 (10 sounds); rejected 4 (air horn) and 12 (crowd "ooh").
>
> **Fetched 9 Oct 2026 (backend engineer):** the owner opened freesound.org and cdn.freesound.org to cloud sessions. All 10 approved pages still read "Creative Commons 0" (read 09:17–09:18 UTC). License proof is a text snapshot per sound (owner's choice: no web.archive.org). The files are the pages' public HQ previews, because originals need a login. They are trimmed, normalized and staged in `content/starter-sounds/` (10 × `.m4a`, 27.2 s, 450 KB total, plus `manifest.json`, `README.md` and `licenses/<slug>.md`). Checklist columns 1, 2, 3 and 5 are done. **Column 4 (listen) is still open:** an agent can't listen, so only objective checks were run (see "Findings, 9 Oct" and "Owner decision needed").

## Summary

- **12 candidates, all CC0 1.0 (Creative Commons 0)**, all from Freesound. Total about 35 s of audio after trimming, about 0.6 MB at AAC 128 kbps, so the APK impact is negligible.
- **No CC-BY in the P1 pack.** P1 has nowhere in the app to show a credit: sound details with credit arrive in P3-03 and the Credits screen in P5-07. CC-BY 4.0 also binds anyone who shares the sound, so every user who posts a video with it would owe a credit, which Memix can't guarantee. Two CC-BY sounds are on the bench for P3 and later.
- **Downloaded and measured on 9 Oct 2026** (the 10 approved sounds). On 8 Oct the network policy blocked freesound.org and cdn.freesound.org, so durations and formats in the candidate tables come from search-index snapshots. All of them matched the live pages on 9 Oct.
- **Evidence status:** on 9 Oct 2026 each live page was read with curl and saved as a text snapshot in `content/starter-sounds/licenses/<slug>.md`. Each snapshot holds the URL, title, author, upload date, the license line as shown, file details, the description, the UTC read time and the SHA-256 of the page HTML. The raw HTML stayed in the session scratchpad. There is no Wayback capture or screenshot, by the owner's choice.

## How the license bar was applied

- **Accepted:** CC0 1.0 ([deed](https://creativecommons.org/publicdomain/zero/1.0/), [legal code](https://creativecommons.org/publicdomain/zero/1.0/legalcode)). It allows copying, modifying and distributing, including commercially, without permission or credit, so bundling the file in a free ad-supported app and letting users publish videos with it are both covered. The dedication can't be revoked, so a later license change on the page doesn't affect a copy downloaded under CC0. That makes the dated snapshot important.
- **Limits of CC0:** it doesn't clear trademark, publicity or privacy rights. That matters for identifiable voices; none of the 12 has one. It also doesn't prove provenance: a CC0 label is the uploader's claim. Freesound bans material taken from commercial recordings, but it can't catch everything. So candidates where the uploader documents how the sound was made (instrument, mic, recorder, synth recipe) were preferred, and the owner listens to each one.
- **Excluded:** NC, ND, Sampling+, "personal use", any license that bars standalone redistribution or use in apps or sound libraries, anything without a clear provenance, anything that names or recreates a specific famous recording, and anything built from commercial sample libraries whose terms forbid redistributing samples or sound effects.
- **Naming rule:** display names describe the sound ("Deep boom"), never a famous source ("Vine boom", "MLG horn").

## Candidates (proposed for the P1 starter pack)

Every candidate: **license** Creative Commons 0 (CC0 1.0 Universal), https://creativecommons.org/publicdomain/zero/1.0/ · **why it qualifies** CC0 allows commercial redistribution, modification and use in users' published videos with no credit and no field-of-use limit · **credit line** none required. We still put a courtesy credit "`<author>` via Freesound" in the manifest and the later Credits screen. · **checked** 8 Oct 2026, page license field via search-index snapshot; the 10 approved pages were re-checked live on 9 Oct 2026 and still read "Creative Commons 0" (snapshots in `content/starter-sounds/licenses/`).

### 1. Sad trombone

| Field | Value |
| --- | --- |
| What / when used | "Wah-wah-wah-waaah" descending trombone. Fails, bad grades, "expectation vs reality". |
| Duration | 5.205 s (page). Bundle about 4–5 s. |
| Source | https://freesound.org/people/kirbydx/sounds/175409/ ("wah wah sad trombone.wav", uploaded 23 Jan 2013, about 20–22K downloads) |
| Author | kirbydx |
| Evidence | License field "Creative Commons 0". Description: the uploader plays the well-known trombone tune themselves, recorded on a Zoom H2next. |
| Risk | **Low–medium.** The four-note gag melody is a traditional stock phrase with no known owner, and this is the uploader's own performance. Owner listens: it must be a live trombone take, not Benboncan's widely copied CC-BY recording (bench B5). |

### 2. Record scratch

| Field | Value |
| --- | --- |
| What / when used | Needle dragged across vinyl. Freeze frame ("yep, that's me"), abrupt stop, plot twist. |
| Duration | 1.876 s (page; FLAC 96 kHz/24-bit stereo). Bundle as is. |
| Source | https://freesound.org/people/musicvision31/sounds/431779/ ("Record Scratch #6", 4 Jun 2018) |
| Author | musicvision31 |
| Evidence | License field "Creative Commons 0". Description: "Typical sound of a needle scratching/sliding on a vinyl record." Tags include comedy and turntable. |
| Risk | **Low–medium.** No gear listed. Owner listens for music under the scratch; there must be none. |

### 3. Boing

| Field | Value |
| --- | --- |
| What / when used | Cartoon jaw-harp boing. Bonks, bounces, awkward jumps, slapstick. |
| Duration | 2.252 s (page; WAV 192 kHz/32-bit stereo). Bundle as is. |
| Source | https://freesound.org/people/reelworldstudio/sounds/161122/ ("Cartoon Boing.wav", about 22.8K downloads) |
| Author | reelworldstudio |
| Evidence | License field "Creative Commons 0". Description: made with a jaw harp, Behringer C2 condenser into an M-Audio ProjectMix, only topped and tailed. |
| Risk | **Low.** Well-documented own recording. |

### 4. Air horn

| Field | Value |
| --- | --- |
| What / when used | Stuttering DJ air-horn blast. Hype, montage parodies, celebrating a win. |
| Duration | 1.610 s (page; mono WAV 22.05 kHz/16-bit). Bundle as is. |
| Source | https://freesound.org/people/jacksonacademyashmore/sounds/414208/ ("Airhorn", Dec 2017, about 17.6K downloads) |
| Author | jacksonacademyashmore |
| Evidence | License field "Creative Commons 0". Description: "made in audacity". Other Freesound users have built edits from it (pfranzen, zar.265). |
| Risk | **Medium.** Provenance is one line. At 22 kHz the top end is dull. The well-known montage air horn is a specific commercial sample: the owner must A/B listen and reject this one if it is that recording. Fallback: a synthesized horn we make ourselves (`license_type = 'own'`). |

### 5. Sitcom laugh

| Field | Value |
| --- | --- |
| What / when used | Small theatre audience laughing, with applause. Laugh track under a bad joke, sarcastic laughter. |
| Duration | 5.799 s (page; WAV 48 kHz/24-bit stereo). Bundle as is. |
| Source | https://freesound.org/people/Kinoton/sounds/371562/ ("Sitcom Laughter with Applause, Small Audience", Dec 2016, about 11.6K downloads) |
| Author | Kinoton |
| Evidence | License field "Creative Commons 0". Description: ORTF pair, Tascam DR-60D MkII, 2× RØDE M5, recorded in a theatre. |
| Risk | **Low.** No identifiable person in a crowd laugh. |

### 6. Whoosh

| Field | Value |
| --- | --- |
| What / when used | Fast air swish. Zoom-ins, transitions, text pop-ins, swipes. |
| Duration | About 0.43 s (page; FLAC 24-bit/44.1 kHz stereo). Bundle as is. |
| Source | https://freesound.org/people/qubodup/sounds/60013/ ("Whoosh", Sep 2008, about 209K downloads) |
| Author | qubodup |
| Evidence | License field "Creative Commons 0". Description: a bamboo stick swung past a Zoom H2, with "I release it to the public domain". |
| Risk | **Low.** The page also says "See profile for CC BY attribution requirements". That line is boilerplate on qubodup's CC0 pages too, and the profile asks for full credit when "the audio is the product". CC0 legally needs no credit, but we honour the request: credit "qubodup" (with the URL) in the manifest and the Credits screen. Optional: email qubodup first. |

### 7. Deep boom

| Field | Value |
| --- | --- |
| What / when used | Huge sub-bass impact with a long tail. The generic "boom" dropped on a punchline, a stare into the camera or a reveal. |
| Duration | 10.500 s (page; FLAC 16-bit/44.1 kHz stereo). Bundle the first about 3 s with a 0.5 s fade-out. |
| Source | https://freesound.org/people/_earthbound_/sounds/856795/ ("Bass Impact - Deep Hit - Rumble - Cinematic Boom v3 alt compressor", 3 Jun 2026) |
| Author | _earthbound_ |
| Evidence | License field "Creative Commons 0". Description: "My original take on a 'Bass Impact'". It was built by layering a 60 Hz sine, a 30 Hz sawtooth and a square wave, plus custom reverb and compression. |
| Risk | **Low** for provenance: fully synthesized, with the recipe documented. It was uploaded only 4 months ago, so re-check that the license field still reads CC0 on the download date. Never label it "Vine boom". |

### 8. Awkward crickets

| Field | Value |
| --- | --- |
| What / when used | Crickets chirping. Awkward silence after a joke flops, "nobody:" setups. |
| Duration | 2:49.002 (page; WAV 48 kHz/16-bit stereo, 30.9 MB). Bundle a clean 3 s excerpt with fades. CC0 allows the edit. |
| Source | https://freesound.org/people/Defelozedd94/sounds/522298/ ("Crickets At Night - Clean sound", about 12.3K downloads) |
| Author | Defelozedd94 |
| Evidence | License field "Creative Commons 0". Description: recorded in a Swiss garden on a Tascam DR-05, storm noise removed in Audacity. |
| Risk | **Low.** Field recording of insects. |

### 9. Wrong buzzer

| Field | Value |
| --- | --- |
| What / when used | Game-show "wrong answer" buzz. Wrong guesses, bad takes, quiz memes. |
| Duration | 0.494 s (page; mono WAV 44.1 kHz/16-bit). Bundle as is. |
| Source | https://freesound.org/people/KevinVG207/sounds/331912/ ("Wrong Buzzer", 28 Dec 2015) |
| Author | KevinVG207 |
| Evidence | License field "Creative Commons 0". Description: "generated in Audacity after I couldn't find any on the internet". |
| Risk | **Low.** Synthesized. |

### 10. Cartoon fall

| Field | Value |
| --- | --- |
| What / when used | Descending slide-whistle-style fall. Someone falls, plans collapse, a stock crashes. |
| Duration | 2.046 s (page; FLAC 44.1 kHz/16-bit stereo). Bundle as is. |
| Source | https://freesound.org/people/plasterbrain/sounds/395443/ ("Cartoon Fall", 15 Jun 2017, about 12.9K downloads) |
| Author | plasterbrain |
| Evidence | License field "Creative Commons 0". Description: made in the Hybrid 3 synth. The profile says everything plasterbrain uploads is CC0, and credit "plasterbrain" (lowercase) is welcome. |
| Risk | **Low.** Synthesized by the uploader. |

### 11. Drum roll

| Field | Value |
| --- | --- |
| What / when used | Snare buzz roll ending on a rimshot. Build-up before a reveal ("and the winner is…"); the rimshot doubles as a punchline hit. |
| Duration | 4.765 s (page; WAV 44.1 kHz/16-bit stereo). Bundle as is. |
| Source | https://freesound.org/people/bigjoedrummer/sounds/77305/ ("buzz roll.wav", 12 Aug 2009, about 20.7K downloads) |
| Author | bigjoedrummer |
| Evidence | License field "Creative Commons 0". Description: "Just a short snare drum buzz roll ending in a rimshot", recorded with a Sony condenser mic into an iRiver H340. |
| Risk | **Low.** Acoustic recording by the drummer. |

### 12. Crowd "ooh"

| Field | Value |
| --- | --- |
| What / when used | Audience "ooooh". Reactions to a roast, a flex or a big reveal. |
| Duration | 25.793 s (page; WAV). Four separate oohs and ahhs; bundle the best "ooh", about 3 s. |
| Source | https://freesound.org/people/noah0189/sounds/264499/ ("Crowd Ooohs and Ahhhs in Excitement", 19 Feb 2015, about 25K downloads) |
| Author | noah0189 |
| Evidence | License field "Creative Commons 0". Description: "Excited audience likes what they see. Four different oohs and ahhs." |
| Risk | **Medium.** No recording details, so provenance rests on the uploader's claim. Owner listens for broadcast or TV character (music bed, announcer, hiss); if it has any, use bench item IENBA "Small crowd reactions" instead (CC0 per the search snapshot, recorded by animation students at Uruguay's National School of Arts; re-verify). |

## Bench (alternates; not in the 12)

| # | Sound | Source | License | Use | Why benched |
| --- | --- | --- | --- | --- | --- |
| B1 | Rim shot ("ba dum tss") | https://freesound.org/people/jmayoff/sounds/256959/ (jmayoff, Dec 2014, "Yamaha e-drums + audacity") | CC0 | Punchline sting | Medium: hits played on an e-drum module are close to redistributing Yamaha's samples. Acceptable if it's a played three-hit pattern, not a single hit. Duration not shown in the snapshot. |
| B2 | Ba dum tss (classic) | https://freesound.org/people/DJczyszy/sounds/431811/ (4.3 s) | CC0 | Punchline sting | Medium: built from "royalty-free samples", which usually may not be redistributed as samples or sound effects. |
| B3 | Synth cricket | https://freesound.org/people/guitarguy1985/sounds/69439/ (2.0 s, purpose-made "awkward silence" filler) | CC0 per listing | Awkward silence | Medium: a commenter says it sounds like the Majora's Mask cricket. Prefer the field recording (#8). |
| B4 | DJ air horn ("bwa-bwa-bwa-BAMMM") | https://freesound.org/people/pfranzen/sounds/528807/ (2 s, Ogg 22 kHz) | CC0 | Hype | An edit of #4, so it carries the same provenance question. |
| B5 | Sad trombone (classic) | https://freesound.org/people/Benboncan/sounds/73581/ (Benboncan's neighbour on trombone, 2009) | CC BY 4.0 on the page (a forum post cites 3.0) | Fails | CC-BY: not before P3-03 and P5-07 can show the credit, and users' exports would owe the credit too. Credit: "Sad Trombone.wav" by Benboncan, https://freesound.org/s/73581/, CC BY 4.0, trimmed and loudness-normalized. |
| B6 | Dramatic sting | https://freesound.org/people/SilverIllusionist/sounds/830184/ (6.5 s) | CC BY 4.0 | Sudden realization, reveal | CC-BY, same as B5. Requested credit: "Dramatic Sting (Sudden Realization)" by Dylan Kelk/Silverillusionist. |

**If a CC-BY sound ever ships:** show the TASL credit (title, author, source link, license link, "trimmed and loudness-normalized") in the sound details sheet (P3-03) and the Credits screen (P5-07, "Every CC-BY item's credit is listed"), and store it in `credit`. Shipping one in P1 would need a credit surface added to P1. That's a scope change, so it goes to the PM and the owner.

## Rejected sources and why (checked 8 Oct 2026)

| Source | Decision | Reason |
| --- | --- | --- |
| **Pixabay** (Pixabay Content License) | Exclude | Prohibited use: "You cannot sell or distribute Content … on a Standalone basis". Standalone means "no creative effort has been applied … and it remains in substantially the same form", and filtering, resizing or cropping still counts as standalone. A trimmed, normalized sound offered in an in-app sound library is substantially the same form. Only pre-9 Jan 2019 uploads were CC0, and today's download page can't prove that. Sources: [license summary](https://pixabay.com/service/license-summary/), [terms](https://pixabay.com/service/terms/), read via search snapshots (site blocked here). |
| **Mixkit** (Sound Effects Free License) | Exclude | "You can't redistribute the Item on its own, as stock, in a tool or template, or with source files." A meme editor's sound library is a tool distributing the item on its own. Sources: [license](https://mixkit.co/license/), [SFX license modal](https://mixkit.co/license/modal/sfxFree/), via search snapshots (site blocked). |
| **ZapSplat** (Standard License) | Exclude | Sounds must not be "the primary value of a product (for example a sound effects app)". Redistribution "in any form, including … apps" is barred, and free accounts must credit. ([license](https://www.zapsplat.com/license-type/standard-license/), via search snapshot) |
| **YouTube** (Audio Library, any download) | Exclude | CLAUDE.md hard rule: no YouTube rips. |
| **Meme soundboard sites** (Myinstants, 101soundboards and similar) | Exclude | User re-uploads of viral clips with unknown rights; the CLAUDE.md "no unknown license" rule. |
| **Freesound items under NC or Sampling+** (e.g. kukla #94104, SgtPepperArc360 #341732, CrazyWashingtonianTrainNut #385821, Timbre's NC uploads) | Exclude | Non-commercial. |
| **Freesound: "Bruh Sound Effect #1"**, Autellaem #534387 (CC0 label) | Exclude | No provenance at all: a 0.7 s MP3 tagged "meme" with only the line "Bruh sound effect by Autellaem". Very likely a re-upload of the viral clip. No truly free "bruh" was found; if the owner wants one, record our own (`'own'`). |
| **Freesound: "Dun Dun Duuun v.01"**, divenorth #215558 | Exclude | The uploader links a YouTube video as "the one you're looking for", so it is a recreation of a specific sting, and the license wasn't confirmed. |
| **Freesound: "Dun dun dun.wav"**, Simon_Lacelle #45654 (CC BY 4.0) | Exclude | CC-BY, and comments show an unresolved ownership dispute with a YouTube channel. |
| **Freesound: qubodup "Dramatic Hit"** #222517 and **"Time Running Out & Buzzer"** #211103 | Exclude | Mixed from other people's sounds; the pages point to CC BY requirements, so the chain is unclear. |
| **Freesound: LordHannes "Bass Boom"** #268084, **nomiqbomi "Timpani Crescendo"** #578574 | Exclude | Made from Logic samples or Logic Pro X. Apple's Logic/GarageBand licence forbids redistributing its audio content as sound effects (from memory, not re-verified this session; excluded under "when unsure, exclude"). |
| **Kenney** (CC0), **OpenGameArt** (CC0 items), **Wikimedia Commons** (PD/CC0) | Acceptable, not used this round | Sites blocked here, and their CC0 audio is mostly game/UI sound rather than meme stings. Good sources for later batches (P3-12). |
| **Sonniss GDC bundles, BBC Sound Effects, SoundBible, SoundJay, Orange Free Sounds, BigSoundBank, Uppbeat, ElevenLabs AI SFX** | Not evaluated | Blocked here, or not needed. From memory, several restrict standalone redistribution or are non-commercial, and AI-generated audio has unclear copyright status. Re-verify before any future use. |

## Network access in this container (8 Oct 2026)

- **Update, 9 Oct 2026:** the owner opened freesound.org and cdn.freesound.org. Pages and previews download with plain curl and no login. Originals still need a login. The other sites below were not re-tested.
- **Blocked by the egress policy** (403 on CONNECT; "do not retry" per the proxy README): freesound.org, cdn.freesound.org, commons.wikimedia.org, upload.wikimedia.org, opengameart.org, pixabay.com, mixkit.co, kenney.nl, creativecommons.org, web.archive.org, archive.org, www.zapsplat.com, sonniss.com, bigsoundbank.com, soundbible.com, www.soundjay.com, uppbeat.io, huggingface.co, cdn.jsdelivr.net, unpkg.com.
- **WebFetch** failed with DNS errors (ENOTFOUND) for freesound.org, pixabay.com and mixkit.co.
- **GitHub:** api.github.com answers only for repos attached to the session; raw.githubusercontent.com works; codeload.github.com is blocked.
- **WebSearch worked.** All license facts above come from search-index snapshots of the named pages.

## Audio processing (run on the 10 approved sounds, 9 Oct 2026)

Scripts live outside this folder, in `scripts/audio/`:

- `normalize.sh <input> <output.m4a> [start_s] [duration_s] [fade_in_s] [fade_out_s]` does the following:
  1. Optionally cuts an excerpt and fades its edges. The fade arguments were added on 9 Oct.
  2. Trims leading and trailing silence below the clip's peak − 60 dB, with a floor of −90 dBFS. On 9 Oct this replaced a fixed −60 dBFS threshold, which would have cut the boing's audible tail: that source peaks at only −22 dBFS and needs +22.5 dB of gain.
  3. Measures with ffmpeg's `ebur128` meter, padding clips shorter than 3 s with silence. Silence doesn't change gated loudness.
  4. Applies one linear gain of min(−16 − I, −1.5 − TP).
  5. Encodes AAC 128 kbps at 48 kHz to `.m4a`.

  Peaky sounds (rimshot, boom attack) finish below −16 LUFS because the peak cap wins. No limiter is used, so nothing gets squashed.
- `measure.sh <file>` prints duration, integrated LUFS, true peak and the stream format.
- Actual layout: the downloads and intermediates stayed in the session scratchpad. The repo holds only `content/starter-sounds/<slug>.m4a`, `manifest.json` (with both SHA-256s), `README.md` and `licenses/<slug>.md`.
- Excerpts used:
  - `deep-boom`: `0 3 0 0.5`, the first 3 s with a 0.5 s fade-out. The source is a flat, deliberately distorted boom for about 3.5 s before it decays.
  - `awkward-crickets`: `129.75 3 0.2 0.5`. The start was picked by analysis, not by ear: in that window 99.5% of the energy is in the 3–8 kHz cricket band, there's no rumble below 300 Hz, and the level is steady.
- Re-running gives byte-identical files with the same ffmpeg (6.1.1, checked on `boing` and `awkward-crickets`).

### Results, 9 Oct 2026

The source is the page's HQ preview (MP3 128 kbps). "Out" is the bundled `.m4a`, measured with `measure.sh`.

| # | Slug | Source len / LUFS / TP | Edit | Gain | Out ms | Out LUFS | Out TP (dBTP) | Ch | Bytes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | sad-trombone | 5.25 s / −14.4 / −3.2 | trim | −2.0 | 4729 | −16.0 | −5.1 | 2 | 77,400 |
| 2 | record-scratch | 1.92 s / −10.2 / +0.8 | trim | −5.8 | 1835 | −16.1 | −4.0 | 2 | 30,469 |
| 3 | boing | 2.28 s / −37.5 / −24.9 | trim | +22.5 | 1668 | −16.2 | −2.5 | 2 | 28,398 |
| 5 | sitcom-laugh | 5.83 s / −18.4 / −1.5 | trim | 0.0 (peak cap) | 5295 | **−18.5** | −1.9 | 2 | 87,260 |
| 6 | whoosh | 0.47 s / −20.5 / −3.6 | trim | +2.1 (peak cap) | 426 | **−18.5** | −1.7 | 2 | 7,501 |
| 7 | deep-boom | 10.53 s / −2.5 / +2.5 | 0–3 s, 0.5 s fade-out | −13.8 | 2997 | −16.0 | −9.4 | 2 | 49,616 |
| 8 | awkward-crickets | 169.03 s / −27.1 / −12.8 | 129.75–132.75 s, fades 0.2/0.5 s | +9.3 | 2982 | −16.1 | −7.2 | 2 | 49,219 |
| 9 | wrong-buzzer | 0.52 s / −5.9 / −0.2 | trim | −10.1 | 494 | −16.1 | −6.1 | 1 | 8,904 |
| 10 | cartoon-fall | 2.09 s / −8.0 / −7.3 | trim | −8.0 | 1991 | −16.0 | −15.2 | 2 | 33,344 |
| 11 | drum-roll | 4.81 s / −22.4 / −0.2 | trim | −1.3 (peak cap) | 4738 | **−23.7** | −1.8 | 2 | 77,872 |
| | **Total** | | | | **27,155** | | | | **449,983** |

All ten are at or under −1.7 dBTP and within −16 ± 0.2 LUFS, except the three peak-capped sounds in bold. No source is silent or hard-clipped: astats flat factor is 0 on all of them. Record scratch and deep boom read slightly above 0 dBFS only as decoded-MP3 overshoot, and the float pipeline keeps it without clipping.

### Findings, 9 Oct 2026

- **Drum roll is quiet (−23.7 LUFS).** The single rimshot peak caps the gain, so the roll sits about 8 LU under the others. It isn't a license issue, so no bench swap. If the P1-08 hand check finds it too quiet next to the others, re-run it with a fast look-ahead limiter on the rimshot (a new optional `normalize.sh` flag). That fix is mine to make and doesn't need the owner. Sitcom laugh and whoosh at −18.5 LUFS are within normal tolerance.
- **Sad trombone is not Benboncan's CC-BY take (bench B5).** I downloaded B5's preview and compared the two. Kirbydx plays the four notes at about 240 → 199 Hz (around Bb3 down to G3) at about 0.9 s per note. B5 plays about 302 → 245 Hz (around D4 down to B3) at about 0.6 s per note. A resampled copy would shift pitch and tempo by the same ratio (1.26x here, against a measured 1.5x tempo ratio). So these are different performances.
- **Record scratch has no music bed.** The gaps hold only 50 Hz mains hum with odd harmonics at about −58 dBFS (−64 dBFS after gain), with no tonal or melodic content. A short pre-scratch at 0–0.3 s is part of the recording.
- **Sitcom laugh** shows a diffuse crowd and broadband claps, with no music or announcer lines. There's a low room tone around 240 Hz at −59 to −72 dBFS.
- **Pages unchanged:** every title, author, duration and file format matches the 8 Oct research. Record scratch shows 4,885 downloads (not recorded on 8 Oct). Deep boom, uploaded in June 2026, still reads CC0. Whoosh still carries qubodup's "See profile for CC BY attribution requirements." line next to the CC0 license. We honour it with a courtesy credit and the URL.
- **Nothing failed:** no license changed, no page is gone, no audio is unusable, so no bench replacement is proposed.

### Owner decision needed: who listens (checklist column 4)

- **Problem:** step 4 says a person listens to each sound to confirm it isn't a famous film, TV, game or meme recording and has no voice. I can only measure the audio, not hear it. The checks above found nothing wrong, but the doc's risk notes for #1 and #2 ask for an ear check.
- **Considerations:** all 10 files together run 27 seconds, and they're in `content/starter-sounds/`. Skipping the ear check saves a minute but leaves the one check that matches recognizability untested.
- **Suggested fixes:**
  - **A (recommended):** the owner plays the 10 files once (about 1 minute) before the P1-08 commit and replies "OK" or names a sound to swap.
  - **B:** the engineer's P1-08 hand check covers it, and the owner listens in the app at the end of the phase.
  - **C:** ship on the objective checks alone.

Self-test on synthetic audio generated with ffmpeg on 8 Oct, before any downloads, using the old fixed −60 dBFS trim:

| Test signal | Input | Output |
| --- | --- | --- |
| 0.45 s quiet sine | −50.0 LUFS | −16.0 LUFS, TP −8.7 dBFS |
| 5 s pink noise | −16.0 LUFS | −16.4 LUFS, TP −4.9 dBFS |
| 1.5 s tone with 1.4 s of silence around it | −22.2 LUFS | −16.0 LUFS, trimmed to 1.50 s |
| 2.5 s excerpt of the pink noise | | −16.4 LUFS |
| 0.6 s peaky transient | −14.3 LUFS, TP +2.3 | −18.9 LUFS, TP −1.0 (peak cap wins, as intended) |

Finding for the P3-12 intake script: on the 0.6 s transient, ffmpeg's `loudnorm` pass 1 reported −21.9 LUFS while `ebur128` reported −14.3, so loudnorm would apply a gain that is about 7.6 dB off. The intake script should measure with `ebur128` and apply a peak-capped linear gain.

## Proposed repo locations for P1-08

> **9 Oct 2026:** the files are staged in `content/starter-sounds/`, and P1-08 decides where the bundled copy lives. The manifest as built uses these field names: `sha256Source` and `sha256Output` (not `sha256Original` and `sha256Bundled`), `licenseCheckedAt` (not `checkedOn`), and `licenseProof`, which is the repo path of the text snapshot (not a Wayback URL). It adds `fetchedFrom`, `previewUrl`, `loudness` and `channels`. `id` = `uuid5(NAMESPACE_URL, "https://freesound.org/s/<id>/")`. License evidence lives in `content/starter-sounds/licenses/`, so no screenshots are needed and `core/data/licenses/starter-sounds/` isn't needed. The rest of this section is the original 8 Oct proposal.

The principal mobile engineer should confirm the module; this is a proposal.

- **Bundled audio and manifest** (shared by Android and iOS): `core/data/src/commonMain/composeResources/files/starter-sounds/`
  - Contents: `<slug>.m4a` (e.g. `sad-trombone.m4a`) and `manifest.json`.
  - `:core:data` is a plain `memix.kmp.library` today, so it would need the Compose resources library, but not Compose UI.
  - Alternative: put them under `composeApp/src/commonMain/composeResources/files/starter-sounds/` behind a `BundledSoundSource` interface declared in `:core:domain`.
  - Media3 needs a URI. Check the current Compose resources file-URI API at ticket start, per CLAUDE.md.
- **`manifest.json`, one entry per sound.** Field names mirror the `sounds` table so the catalog and the bundle share one shape:
  - `id`: a stable UUID, reused as the remote `sounds.id` at P3-12, so projects and favorites that point at a starter sound still resolve.
  - `file`, `title` (en/id/es/pt/hi), `durationMs`, `author`.
  - `sourceUrl`, `licenseType` (`CC0`), `licenseName`, `licenseUrl`, `licenseProof` (Wayback snapshot URL).
  - `credit` (null for CC0), `courtesyCredit`, `edits` ("silence trimmed, normalized to −16 LUFS, AAC 128 kbps").
  - `checkedOn`, `approvedOn`, `sha256Original`, `sha256Bundled`.
- **License evidence** (in the repo, not bundled): `core/data/licenses/starter-sounds/`, mirroring `core/designsystem/licenses/` for the fonts. Contents:
  - `README.md`: a per-sound table of source, author, license, snapshot URL, original sha256, checked and approved dates.
  - `CC0-1.0.txt`: the legal code.
  - `<slug>-license.png`: a screenshot of each page's license field.
- **Docs in the same commit:** TECHNICAL_DESIGN.md → Backend/CMS (starter-pack ids reserved in `sounds`, same license fields). `docs/pm/` gets the owner's approval record.

## Owner approval checklist

Steps for each sound:

1. Open the Freesound page and confirm the license line reads "Creative Commons 0". Note the date.
2. Save a text snapshot of the page to `content/starter-sounds/licenses/<slug>.md` (owner's choice: no web.archive.org; raw HTML stays out of the repo, its SHA-256 goes in the snapshot).
3. Download the file and record its sha256. Originals need a Freesound login, so the P1 pack uses the page's public HQ preview (MP3 128 kbps) and says so in the manifest (`fetchedFrom`).
4. Listen. It must not be a recognizable film, TV, game, song or viral-meme recording, and it must have no identifiable voice.
5. Run `scripts/audio/normalize.sh`. Then `scripts/audio/measure.sh` must show −16 ± 1 LUFS (or lower when peak-capped) and true peak ≤ −1 dBTP.
6. Tick "Approved", or "Rejected" with a reason.

| # | Sound | Author | 1 License still CC0 (UTC) | 2 Text snapshot | 3 HQ preview + sha256 | 4 Listened, clean | 5 Normalized and measured | Approved | Rejected (reason) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Sad trombone | kirbydx | [x] 9 Oct 2026 09:17:22Z, "Creative Commons 0" | [x] `licenses/sad-trombone.md` | [x] `de4a029a…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: not B5 (pitch and tempo differ) | [x] 4729 ms, −16.0 LUFS, −5.1 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 2 | Record scratch | musicvision31 | [x] 9 Oct 2026 09:17:40Z, "Creative Commons 0" | [x] `licenses/record-scratch.md` | [x] `18e5517e…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: no music bed in the gaps (50 Hz hum only) | [x] 1835 ms, −16.1 LUFS, −4.0 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 3 | Boing | reelworldstudio | [x] 9 Oct 2026 09:17:43Z, "Creative Commons 0" | [x] `licenses/boing.md` | [x] `c354584c…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: quiet source, tail kept | [x] 1668 ms, −16.2 LUFS, −2.5 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 4 | Air horn | jacksonacademyashmore | n/a (rejected) | | | | | [ ] | [x] owner, 8 Oct 2026: thin provenance (medium risk) |
| 5 | Sitcom laugh | Kinoton | [x] 9 Oct 2026 09:17:45Z, "Creative Commons 0" | [x] `licenses/sitcom-laugh.md` | [x] `9b812927…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: crowd and claps only | [x] 5295 ms, −18.5 LUFS (peak cap), −1.9 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 6 | Whoosh | qubodup | [x] 9 Oct 2026 09:17:48Z, "Creative Commons 0" | [x] `licenses/whoosh.md` | [x] `ebdb219e…` (full hash in manifest) | [ ] ear check open (owner decision above) | [x] 426 ms, −18.5 LUFS (peak cap), −1.7 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 7 | Deep boom | _earthbound_ | [x] 9 Oct 2026 09:17:51Z, "Creative Commons 0" | [x] `licenses/deep-boom.md` | [x] `dbf2675d…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: distortion is by design | [x] first 3 s + 0.5 s fade; 2997 ms, −16.0 LUFS, −9.4 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 8 | Awkward crickets | Defelozedd94 | [x] 9 Oct 2026 09:17:54Z, "Creative Commons 0" | [x] `licenses/awkward-crickets.md` | [x] `8ae77b16…` (full hash in manifest) | [ ] ear check open (owner decision above); objective: 3–8 kHz only, no rumble | [x] 129.75–132.75 s + fades; 2982 ms, −16.1 LUFS, −7.2 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 9 | Wrong buzzer | KevinVG207 | [x] 9 Oct 2026 09:17:57Z, "Creative Commons 0" | [x] `licenses/wrong-buzzer.md` | [x] `4a15cae7…` (full hash in manifest) | [ ] ear check open (owner decision above) | [x] 494 ms, −16.1 LUFS, −6.1 dBTP (mono) | [x] owner, 8 Oct 2026 | [ ] |
| 10 | Cartoon fall | plasterbrain | [x] 9 Oct 2026 09:18:00Z, "Creative Commons 0" | [x] `licenses/cartoon-fall.md` | [x] `8285df3f…` (full hash in manifest) | [ ] ear check open (owner decision above) | [x] 1991 ms, −16.0 LUFS, −15.2 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 11 | Drum roll | bigjoedrummer | [x] 9 Oct 2026 09:18:02Z, "Creative Commons 0" | [x] `licenses/drum-roll.md` | [x] `2f3d571b…` (full hash in manifest) | [ ] ear check open (owner decision above) | [x] 4738 ms, **−23.7 LUFS** (rimshot caps gain; see Findings), −1.8 dBTP | [x] owner, 8 Oct 2026 | [ ] |
| 12 | Crowd "ooh" | noah0189 | n/a (rejected) | | | | | [ ] | [x] owner, 8 Oct 2026: thin provenance (medium risk) |
| B1–B4 | Bench swaps (CC0) | see bench | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| B5–B6 | Bench (CC-BY, P3+ only) | see bench | not for P1 | | | | | | |

Owner sign-off: ______________________ Date: ____________
