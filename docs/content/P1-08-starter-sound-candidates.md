# P1-08 starter sounds: candidate list for the owner's license approval

Prepared by the backend engineer on 8 Oct 2026. No audio is in the repo yet: this list and the two scripts in `scripts/audio/` are committed so the research survives the cloud session. The owner approves each sound before it ships (TICKETS.md: "P1-08 waits until the owner approves each starter-sound license (CC0 or equivalent)"). This is not legal advice; the PRD still recommends an IP-lawyer review before launch.

## Summary

- **12 candidates, all CC0 1.0 (Creative Commons 0)**, all from Freesound. Total about 35 s of audio after trimming, about 0.6 MB at AAC 128 kbps, so the APK impact is negligible.
- **No CC-BY in the P1 pack.** P1 has nowhere in the app to show a credit: sound details with credit arrive in P3-03 and the Credits screen in P5-07. CC-BY 4.0 also binds anyone who shares the sound, so every user who posts a video with it would owe a credit, which Memix can't guarantee. Two CC-BY sounds are on the bench for P3 and later.
- **Nothing downloaded or measured.** This container's network policy blocks freesound.org and cdn.freesound.org, along with every other sound and license site (list below). Durations and formats come from each Freesound page's file details, read through search-index snapshots on 8 Oct 2026. The normalize and measure scripts are ready and self-tested on synthetic audio (see "Audio processing").
- **Evidence status:** for each sound, the page's license field and description were read on 8 Oct 2026 through the search index. The live page, a Wayback snapshot and a screenshot could not be captured here (web.archive.org is blocked too). Capturing them is the first item on the approval checklist.

## How the license bar was applied

- **Accepted:** CC0 1.0 ([deed](https://creativecommons.org/publicdomain/zero/1.0/), [legal code](https://creativecommons.org/publicdomain/zero/1.0/legalcode)). It allows copying, modifying and distributing, including commercially, without permission or credit, so bundling the file in a free ad-supported app and letting users publish videos with it are both covered. The dedication can't be revoked, so a later license change on the page doesn't affect a copy downloaded under CC0. That makes the dated snapshot important.
- **Limits of CC0:** it doesn't clear trademark, publicity or privacy rights. That matters for identifiable voices; none of the 12 has one. It also doesn't prove provenance: a CC0 label is the uploader's claim. Freesound bans material taken from commercial recordings, but it can't catch everything. So candidates where the uploader documents how the sound was made (instrument, mic, recorder, synth recipe) were preferred, and the owner listens to each one.
- **Excluded:** NC, ND, Sampling+, "personal use", any license that bars standalone redistribution or use in apps or sound libraries, anything without a clear provenance, anything that names or recreates a specific famous recording, and anything built from commercial sample libraries whose terms forbid redistributing samples or sound effects.
- **Naming rule:** display names describe the sound ("Deep boom"), never a famous source ("Vine boom", "MLG horn").

## Candidates (proposed for the P1 starter pack)

Every candidate: **license** Creative Commons 0 (CC0 1.0 Universal), https://creativecommons.org/publicdomain/zero/1.0/ · **why it qualifies** CC0 allows commercial redistribution, modification and use in users' published videos with no credit and no field-of-use limit · **credit line** none required. We still put a courtesy credit "`<author>` via Freesound" in the manifest and the later Credits screen. · **checked** 8 Oct 2026, page license field via search-index snapshot; the live page must be re-checked at download.

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

- **Blocked by the egress policy** (403 on CONNECT; "do not retry" per the proxy README): freesound.org, cdn.freesound.org, commons.wikimedia.org, upload.wikimedia.org, opengameart.org, pixabay.com, mixkit.co, kenney.nl, creativecommons.org, web.archive.org, archive.org, www.zapsplat.com, sonniss.com, bigsoundbank.com, soundbible.com, www.soundjay.com, uppbeat.io, huggingface.co, cdn.jsdelivr.net, unpkg.com.
- **WebFetch** failed with DNS errors (ENOTFOUND) for freesound.org, pixabay.com and mixkit.co.
- **GitHub:** api.github.com answers only for repos attached to the session; raw.githubusercontent.com works; codeload.github.com is blocked.
- **WebSearch worked.** All license facts above come from search-index snapshots of the named pages.

## Audio processing (ready; not run on real files)

Scripts live outside this folder, in `scripts/audio/`:

- `normalize.sh <input> <output.m4a> [start_s] [duration_s]` does the following:
  1. Optionally cuts an excerpt.
  2. Trims leading and trailing silence at −60 dB.
  3. Measures with ffmpeg's `ebur128` meter, padding clips shorter than 3 s with silence. Silence doesn't change gated loudness.
  4. Applies one linear gain of min(−16 − I, −1.5 − TP).
  5. Encodes AAC 128 kbps at 48 kHz to `.m4a`.

  Peaky sounds (rimshot, boom attack) finish below −16 LUFS because the peak cap wins. No limiter is used, so nothing gets squashed.
- `measure.sh <file>` prints duration, integrated LUFS, true peak and the stream format.
- Planned layout once downloads are possible: `starter-sounds/originals/` (untouched downloads plus `sha256sums.txt`) and `starter-sounds/normalized/<slug>.m4a`.
- Excerpt arguments: crickets `<start> 3`, crowd ooh `<start> 3`, deep boom `0 3`, then add a fade. Pick the start times by ear.

Self-test on synthetic audio generated with ffmpeg (no downloads):

| Test signal | Input | Output |
| --- | --- | --- |
| 0.45 s quiet sine | −50.0 LUFS | −16.0 LUFS, TP −8.7 dBFS |
| 5 s pink noise | −16.0 LUFS | −16.4 LUFS, TP −4.9 dBFS |
| 1.5 s tone with 1.4 s of silence around it | −22.2 LUFS | −16.0 LUFS, trimmed to 1.50 s |
| 2.5 s excerpt of the pink noise | | −16.4 LUFS |
| 0.6 s peaky transient | −14.3 LUFS, TP +2.3 | −18.9 LUFS, TP −1.0 (peak cap wins, as intended) |

Finding for the P3-12 intake script: on the 0.6 s transient, ffmpeg's `loudnorm` pass 1 reported −21.9 LUFS while `ebur128` reported −14.3, so loudnorm would apply a gain that is about 7.6 dB off. The intake script should measure with `ebur128` and apply a peak-capped linear gain.

## Proposed repo locations for P1-08 (not created)

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
2. Save a Wayback snapshot (`https://web.archive.org/save/<page URL>`) and a screenshot of the license field.
3. Download the original (Freesound needs a login for originals) and record its sha256.
4. Listen. It must not be a recognizable film, TV, game, song or viral-meme recording, and it must have no identifiable voice.
5. Run `scripts/audio/normalize.sh`. Then `scripts/audio/measure.sh` must show −16 ± 1 LUFS (or lower when peak-capped) and true peak ≤ −1 dBTP.
6. Tick "Approved", or "Rejected" with a reason.

| # | Sound | Author | 1 License still CC0 (date) | 2 Snapshot + screenshot | 3 Original + sha256 | 4 Listened, clean | 5 Normalized and measured | Approved | Rejected (reason) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Sad trombone | kirbydx | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 2 | Record scratch | musicvision31 | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 3 | Boing | reelworldstudio | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 4 | Air horn | jacksonacademyashmore | [ ] ____ | [ ] | [ ] | [ ] A/B vs the famous montage horn | [ ] | [ ] | [ ] |
| 5 | Sitcom laugh | Kinoton | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 6 | Whoosh | qubodup | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 7 | Deep boom | _earthbound_ | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 8 | Awkward crickets | Defelozedd94 | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 9 | Wrong buzzer | KevinVG207 | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 10 | Cartoon fall | plasterbrain | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 11 | Drum roll | bigjoedrummer | [ ] ____ | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| 12 | Crowd "ooh" | noah0189 | [ ] ____ | [ ] | [ ] | [ ] check for broadcast sound | [ ] | [ ] | [ ] |
| B1–B4 | Bench swaps (CC0) | see bench | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| B5–B6 | Bench (CC-BY, P3+ only) | see bench | not for P1 | | | | | | |

Owner sign-off: ______________________ Date: ____________
