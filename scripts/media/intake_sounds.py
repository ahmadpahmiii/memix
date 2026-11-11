#!/usr/bin/env python3
"""Content intake for meme sounds (P3-12a): license gate, audio processing, review table, upload, SQL.

Desktop tool. ffmpeg is allowed here because it never ships in the app. Python 3.9+, standard library only.

    intake_sounds.py init     <batch_dir>                    create batch.json and print an example row
    intake_sounds.py fetch    <batch_dir> <freesound url or id>... --category <id> [--dry-run]
    intake_sounds.py prepare  <batch_dir> [--dry-run] [--force]   gate, trim, -16 LUFS, AAC, waveform, review.md
    intake_sounds.py approve  <batch_dir> --all | --ids <id8>,.. [--reject <id8>,..] [--by NAME]
    intake_sounds.py load     <batch_dir> [--dry-run]              upload approved rows, write load.sql
    intake_sounds.py starters <batch_dir>                          waveform-only batch for the 10 live starter sounds

A batch is one folder (content/intake/<name>/, about 50 sounds) holding batch.json, source/, proofs/, review.md,
out/ (the m4a and waveform files) and load.sql. Only batch.json, proofs/, review.md and load.sql are committed.

Rules
- Licence gate (PRD, Content and licensing policy; owner, 11 Nov 2026). Accepted: CC0, CC-BY (with credit) and own.
  royalty-free and licensed are valid database values but need the owner's separate approval, so this script
  refuses them. Every row needs source_url, license_type, license_proof, title.en and a known sound category.
  The gate runs at prepare, at approve and again at load, so a row edited after approval is refused.
- Secrets come from the repo's .env file only (never the environment or arguments) and are never printed:
  FREESOUND_API_KEY (fetch) and SUPABASE_URL plus SUPABASE_SECRET_KEY (load, through upload_media.py).
  --dry-run needs no .env and sends nothing. Cloud sessions dry-run; the owner runs the live steps on the Mac.
- Keys are immutable: sounds/<yyyy>/<mm>/<id>.m4a and waveforms/<yyyy>/<mm>/<id>.json, never overwritten. The id is
  uuid5(source_url) (the same rule as the starter sounds), so a row cannot be loaded twice. A changed file needs a
  new "revision" on its row.
- Waveform file: {"v":1,"peaks":[64 numbers from 0 to 1, three decimals]}; max of each 1/64 of the sound,
  scaled so the loudest bucket is 1 (each sound shows its own shape, not its loudness; P3-03 spec).
"""
import argparse
import array
import datetime
import hashlib
import importlib.util
import json
import re
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NORMALIZE = ROOT / "scripts/audio/normalize.sh"
UPLOAD_PY = ROOT / "scripts/media/upload_media.py"
SEED_CATEGORIES = ROOT / "supabase/migrations/20261007170716_seed_sound_categories.sql"

ACCEPTED_LICENSES = ("CC0", "CC-BY", "own")  # PRD, 11 Nov 2026
NEEDS_OWNER_APPROVAL = ("royalty-free", "licensed")  # allowed by the table's check, not by this script
BUCKETS = 64
MAX_SECONDS = 30
MIN_MS = 100
BATCH_SIZE_HINT = 60
TRUE_PEAK_LIMIT = -1.0
PLACEHOLDER = re.compile(r"^\s*(todo|tbd|n/?a|none|null|unknown|\?+|-+)\s*$", re.I)
URL = re.compile(r"^https?://[^\s]+$")
HTTPS_URL = re.compile(r"^https://[^\s]+$")
UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
SAFE_NAME = re.compile(r"^[A-Za-z0-9_-]+$")
MONTH = re.compile(r"^\d{4}/\d{2}$")
TITLE_LANGS = ("en", "id", "es", "pt", "hi")
CC_BY_CREDIT = re.compile(r"\bby\b|CC[ -]BY", re.I)
STRIPPED = ("license_type", "source_url", "license_proof", "added_by", "credit", "category_id")
REGION = re.compile(r"^([A-Z]{2}|global)$")
# Freesound's licence URLs -> our license_type; anything else (by-nc, by-sa, by-nd, sampling+) is refused.
FREESOUND_LICENSES = {
    "creativecommons.org/publicdomain/zero/1.0": "CC0",
    "creativecommons.org/licenses/by/3.0": "CC-BY",
    "creativecommons.org/licenses/by/4.0": "CC-BY",
}


# ---------------------------------------------------------------- licence gate

def sound_categories():
    """Category ids come from the seed migration, so the script cannot drift from the database."""
    return set(re.findall(r"\('(sound_[a-z_]+)'", SEED_CATEGORIES.read_text(encoding="utf-8")))


def license_url_type(url):
    bare = re.sub(r"^https?://", "", url.strip()).rstrip("/")
    return FREESOUND_LICENSES.get(bare)


def row_id(row):
    if row.get("id"):
        return str(row["id"])
    seed = row.get("source_url", "") + (f"#r{row['revision']}" if row.get("revision") else "")
    return str(uuid.uuid5(uuid.NAMESPACE_URL, seed))


def inside(path, base):
    """True when path, once resolved (symlinks and .. included), is under base."""
    try:
        Path(path).resolve().relative_to(Path(base).resolve())
        return True
    except (ValueError, OSError):
        return False


def proof_file(proof):
    """The proof as a file under content/ (the database stores that repo path), or None for an https URL or a bad value."""
    if HTTPS_URL.match(proof):
        return None
    path = ROOT / proof
    return path if not Path(proof).is_absolute() and path.is_file() and inside(path, ROOT / "content") else None


def proof_ok(proof):
    return bool(HTTPS_URL.match(proof)) or proof_file(proof) is not None


def is_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def processed_path(batch_dir, relative):
    """A processed file path, only if it resolves inside <batch>/out; otherwise None."""
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        return None
    path = batch_dir / relative
    return path if inside(path, batch_dir / "out") else None


def gate(row, batch_dir, categories):
    """All reasons the row cannot enter the catalog; an empty list means it may."""
    problems = []
    if not isinstance(row.get("id"), str) or not UUID.match(row["id"]):
        problems.append("id must be a lowercase UUID")

    def text(name):
        value = row.get(name)
        if not isinstance(value, str) or not value.strip() or PLACEHOLDER.match(value):
            problems.append(f"{name} is missing")
            return None
        return value.strip()

    source_url = text("source_url")
    license_type = text("license_type")
    proof = text("license_proof")
    if license_type is not None and license_type not in ACCEPTED_LICENSES:
        if license_type in NEEDS_OWNER_APPROVAL:
            problems.append(f"license_type '{license_type}' needs the owner's separate approval; "
                            f"this script accepts only {', '.join(ACCEPTED_LICENSES)} (PRD, 11 Nov 2026)")
        else:
            problems.append(f"license_type '{license_type}' is outside the accepted set {', '.join(ACCEPTED_LICENSES)}")
    if source_url and license_type != "own" and not URL.match(source_url):
        problems.append("source_url must be the http(s) page where the sound and its license are shown")
    if source_url and license_type == "own" and not (URL.match(source_url) or source_url.startswith("own:")):
        problems.append("source_url for an own sound is a page URL or 'own:<where it was recorded or recreated>'")
    if proof and not proof_ok(proof):
        problems.append(f"license_proof '{proof}' is neither an https URL nor a file under content/ in this repo (path from the repo root)")
    credit = row.get("credit")
    credit = credit.strip() if isinstance(credit, str) else None
    if license_type == "CC-BY" and not credit:
        problems.append("credit is required for CC-BY")
    if credit is not None and PLACEHOLDER.match(credit):
        problems.append("credit is a placeholder")
    title_values = [str(v).strip().lower() for v in (row.get("title") or {}).values()] if isinstance(row.get("title"), dict) else []
    if license_type == "CC-BY" and credit and (credit.lower() in title_values or not CC_BY_CREDIT.search(credit)):
        problems.append("a CC-BY credit must be more than the title: name the author and source, e.g. 'Title by Author, Freesound (CC BY 4.0, edited)'")
    license_url = row.get("license_url")
    if license_url and license_type in ("CC0", "CC-BY"):
        found = license_url_type(license_url)
        if found is None:
            problems.append(f"license_url '{license_url}' is not CC0 or CC-BY (NC, SA and ND licenses bar standalone redistribution)")
        elif found != license_type:
            problems.append(f"license_url says {found} but license_type is {license_type}")

    title = row.get("title")
    if not isinstance(title, dict) or not str(title.get("en", "")).strip():
        problems.append("title.en is missing")
    elif any(len(str(v)) > 60 for v in title.values()):
        problems.append("a title is longer than 60 characters")
    if isinstance(title, dict) and set(title) - set(TITLE_LANGS):
        problems.append(f"title languages must be {', '.join(TITLE_LANGS)}; found {', '.join(sorted(set(title) - set(TITLE_LANGS)))}")
    category = row.get("category_id")
    if category not in categories:
        problems.append(f"category_id '{category}' is not a sound category ({', '.join(sorted(categories))})")
    regions = row.get("regions", ["global"])
    if not isinstance(regions, list) or not regions or not all(isinstance(r, str) and REGION.match(r) for r in regions):
        problems.append("regions must be a list of 'global' or two-letter upper-case country codes")
    tags = row.get("tags", [])
    if not isinstance(tags, list) or not all(isinstance(t, str) and t.strip() for t in tags) or len(tags) > 10:
        problems.append("tags must be a list of at most 10 non-empty strings")
    if not str(row.get("added_by", "")).strip():
        problems.append("added_by is missing")
    if not str(row.get("source", "")).strip():
        problems.append("source (the local audio file) is missing")
    trim = row.get("trim")
    if trim is not None:
        if not isinstance(trim, dict) or set(trim) - {"start", "duration", "fade_in", "fade_out"}:
            problems.append("trim must be an object with start, duration, fade_in, fade_out")
        else:
            for name in ("start", "fade_in", "fade_out"):
                if name in trim and not (is_number(trim[name]) and trim[name] >= 0):
                    problems.append(f"trim.{name} must be a number of seconds, 0 or more")
            if trim.get("duration") is not None and not (is_number(trim["duration"]) and trim["duration"] > 0):
                problems.append("trim.duration must be null or a number of seconds above 0")
    processed = row.get("processed")
    if processed:
        if not isinstance(processed, dict):
            problems.append("processed record is damaged; run prepare --force")
        else:
            for name in ("file", "waveform"):
                if (name in processed or name == "waveform") and processed_path(batch_dir, processed.get(name)) is None:
                    problems.append(f"processed.{name} must be a file inside this batch's out/ folder")
            if not isinstance(processed.get("durationMs"), int) or isinstance(processed.get("durationMs"), bool):
                problems.append("processed.durationMs must be an integer")
    return problems


def warnings(row):
    title = row.get("title") or {}
    missing = [lang for lang in ("id", "es", "pt", "hi") if not str(title.get(lang, "")).strip()]
    return [f"title has no {', '.join(missing)} (machine drafts are fine until P5-06, but search needs them)"] if missing else []


def canonical_hash(row):
    """What the owner approved: every catalog field plus the processed file. Editing any of them voids the approval."""
    fields = {k: row.get(k) for k in ("title", "category_id", "regions", "tags", "source_url", "license_type",
                                      "license_proof", "credit", "added_by")}
    fields["file"] = (row.get("processed") or {}).get("sha256")
    fields["waveform"] = (row.get("processed") or {}).get("waveformSha256")
    proof = proof_file(row.get("license_proof") or "")
    fields["proofSha256"] = hashlib.sha256(proof.read_bytes()).hexdigest() if proof else None
    return hashlib.sha256(json.dumps(fields, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


# ---------------------------------------------------------------- batch file

def batch_path(batch_dir):
    return batch_dir / "batch.json"


def load_batch(batch_dir):
    path = batch_path(batch_dir)
    if not path.is_file():
        sys.exit(f"{path} not found. Run: intake_sounds.py init {batch_dir}")
    if not inside(batch_dir, ROOT):
        sys.exit(f"{batch_dir} is outside the repo; batches live in content/intake/<name>/ so proof paths stay repo-relative.")
    batch = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(batch.get("batch"), str) or not SAFE_NAME.match(batch["batch"]):
        sys.exit("batch.json: 'batch' must use only letters, digits, _ and -")
    if not isinstance(batch.get("month"), str) or not MONTH.match(batch["month"]):
        sys.exit("batch.json: 'month' must look like 2026/11")
    for row in batch["rows"]:
        for name in STRIPPED:  # the gate, the hash, the SQL and the id all see the same value
            if isinstance(row.get(name), str):
                row[name] = row[name].strip() or None if name == "credit" else row[name].strip()
        if isinstance(row.get("title"), dict):
            for lang, value in list(row["title"].items()):
                if isinstance(value, str):
                    row["title"][lang] = unicodedata.normalize("NFC", value)  # Devanagari search: one spelling per character
        row["id"] = row_id(row)
    return batch


def save_batch(batch_dir, batch):
    batch_path(batch_dir).write_text(json.dumps(batch, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def example_row():
    return {
        "source": "source/example.wav",
        "title": {"en": "Example sound", "id": "", "es": "", "pt": "", "hi": ""},
        "category_id": "sound_reactions",
        "regions": ["global"],
        "tags": ["example", "tag"],
        "source_url": "https://freesound.org/people/<user>/sounds/<id>/",
        "license_type": "CC0",
        "license_url": "https://creativecommons.org/publicdomain/zero/1.0/",
        "license_proof": "content/intake/<batch>/proofs/<id>.md",
        "credit": None,
        "added_by": "owner",
        "trim": {"start": 0, "duration": None, "fade_in": 0, "fade_out": 0},
    }


def month_folder():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y/%m")


def run(cmd, **kw):
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kw)


def need_ffmpeg():
    for tool in ("ffmpeg", "ffprobe", "bash"):
        if not shutil.which(tool):
            sys.exit(f"{tool} is required on this machine (desktop intake only; it never ships in the app).")


def read_env_names(path, wanted):
    """Only the names asked for; a quoted or unquoted KEY=value per line. Values are never printed."""
    values = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            name, sep, value = line.strip().partition("=")
            if sep and not name.startswith("#") and name.strip() in wanted:
                values[name.strip()] = value.strip().strip("'\"")
    return values


def short(row):
    return row["id"][:8]


# ---------------------------------------------------------------- audio

def probe_ms(path):
    out = run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)]).stdout.strip()
    return round(float(out) * 1000)


def measure(path):
    """Integrated loudness (LUFS) and true peak (dBTP), short clips padded to 3 s for the meter like measure.sh."""
    err = subprocess.run(["ffmpeg", "-nostdin", "-hide_banner", "-i", str(path), "-af", "apad=whole_dur=3,ebur128=peak=true",
                          "-f", "null", "-"], capture_output=True, text=True).stderr
    summary = err[err.rfind("Summary:"):]
    lufs = re.search(r"\bI:\s+(-?[\d.]+|-inf) LUFS", summary)
    peak = re.search(r"\bPeak:\s+(-?[\d.]+|-inf) dBFS", summary)
    if not lufs or not peak:
        sys.exit(f"Could not measure loudness of {path.name}")
    return float(lufs.group(1)), float(peak.group(1))


def waveform(path):
    raw = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", str(path), "-ac", "1", "-ar", "16000", "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    samples = array.array("f")
    samples.frombytes(raw[: len(raw) // 4 * 4])
    if sys.byteorder == "big":
        samples.byteswap()
    n = len(samples)
    peaks = [max((abs(x) for x in samples[i * n // BUCKETS:(i + 1) * n // BUCKETS]), default=0.0) for i in range(BUCKETS)]
    top = max(peaks)
    peaks = [round(p / top, 3) if top > 0 else 0.0 for p in peaks]
    return json.dumps({"v": 1, "peaks": peaks}, separators=(",", ":")).encode()


def process(row, batch_dir):
    """Source file -> out/<id>.m4a and out/<id>.waveform.json. Returns the processed record."""
    source = Path(row["source"])
    source = source if source.is_absolute() else batch_dir / source
    out_dir = batch_dir / "out"
    tmp = out_dir / ".tmp"
    tmp.mkdir(parents=True, exist_ok=True)
    trim = row.get("trim") or {}
    tmp_m4a = tmp / f"{row['id']}.m4a"
    cap = -1.5
    for attempt in (1, 2):
        run(["bash", str(NORMALIZE), str(source), str(tmp_m4a), str(trim.get("start", 0)),
             "" if trim.get("duration") is None else str(trim["duration"]),
             str(trim.get("fade_in", 0)), str(trim.get("fade_out", 0)), str(cap)])
        lufs, peak = measure(tmp_m4a)
        if peak <= TRUE_PEAK_LIMIT or attempt == 2:
            break
        cap -= (peak - TRUE_PEAK_LIMIT) + 0.2  # AAC overshoot: encode again with more headroom
    m4a = out_dir / f"{row['id']}.m4a"
    wf = out_dir / f"{row['id']}.waveform.json"
    shutil.move(str(tmp_m4a), m4a)
    wf.write_bytes(waveform(m4a))
    for leftover in tmp.glob(f"{row['id']}*"):
        leftover.unlink()
    return {
        "file": f"out/{m4a.name}", "waveform": f"out/{wf.name}",
        "durationMs": probe_ms(m4a), "lufs": lufs, "truePeakDbtp": peak,
        "bytes": m4a.stat().st_size, "sha256": hashlib.sha256(m4a.read_bytes()).hexdigest(),
        "waveformSha256": hashlib.sha256(wf.read_bytes()).hexdigest(),
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    }


def process_waveform_only(row, batch_dir):
    """Already-live sound (the starters): the waveform of the exact file that is live; no re-encode, no new audio file."""
    source = Path(row["source"])
    if hashlib.sha256(source.read_bytes()).hexdigest() != row.get("expectSha256"):
        raise ValueError("the audio file is not the one that is live (sha256 differs from the manifest)")
    wf = batch_dir / "out" / f"{row['id']}.waveform.json"
    (batch_dir / "out").mkdir(exist_ok=True)
    wf.write_bytes(waveform(source))
    lufs, peak = measure(source)
    return {"waveform": f"out/{wf.name}", "waveformSha256": hashlib.sha256(wf.read_bytes()).hexdigest(),
            "durationMs": probe_ms(source), "lufs": lufs, "truePeakDbtp": peak, "bytes": source.stat().st_size,
            "sha256": row["expectSha256"], "sourceSha256": row["expectSha256"]}


def processing_problems(p, waveform_only=False):
    if waveform_only:
        return []
    problems = []
    if p["durationMs"] < MIN_MS:
        problems.append(f"only {p['durationMs']} ms long after trimming silence (minimum {MIN_MS} ms)")
    if p["durationMs"] > MAX_SECONDS * 1000:
        problems.append(f"{p['durationMs'] / 1000:.1f} s long; trim it to {MAX_SECONDS} s or less (trim.start and trim.duration)")
    if p["truePeakDbtp"] > TRUE_PEAK_LIMIT:
        problems.append(f"true peak {p['truePeakDbtp']} dBTP is above {TRUE_PEAK_LIMIT}")
    if p["bytes"] > 2 * 1024 * 1024:
        problems.append("file is larger than 2 MB")
    return problems


# ---------------------------------------------------------------- review table

def md(text):
    return str(text if text is not None else "").replace("|", "\\|").replace("\n", " ")


def write_review(batch_dir, batch, refused):
    rows = [r for r in batch["rows"] if r.get("processed")]
    lines = [f"# Review table: {batch['batch']}", "",
             f"{len(rows)} sounds to review, {len(refused)} refused. Accepted licenses: CC0, CC-BY with an in-app credit, own "
             "(owner rule, 11 Nov 2026). Open each source URL and proof, listen to the file in `out/`, then approve with "
             f"`python3 scripts/media/intake_sounds.py approve {batch_dir} --all` (or `--ids`, `--reject`).", "",
             "| # | id | Title (en) | Category | Source URL | License | Proof | Credit | Length | Loudness | Status |",
             "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for i, r in enumerate(rows, 1):
        p = r["processed"]
        flag = " (quiet: peak-capped)" if p["lufs"] < -20 else ""
        lines.append(f"| {i} | {short(r)} | {md(r['title']['en'])} | {r['category_id']} | {md(r['source_url'])} | {r['license_type']} | "
                     f"{md(r['license_proof'])} | {md(r.get('credit'))} | {p['durationMs'] / 1000:.2f} s | "
                     f"{p['lufs']:.1f} LUFS, {p['truePeakDbtp']:.1f} dBTP{flag} | {r.get('status', 'pending')} |")
    if refused:
        lines += ["", "## Refused (fix in batch.json, then run prepare again)", "", "| id | Title | Reasons |", "| --- | --- | --- |"]
        for r, problems in refused:
            lines.append(f"| {short(r)} | {md((r.get('title') or {}).get('en', ''))} | {md('; '.join(problems))} |")
    (batch_dir / "review.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- commands

def cmd_init(args):
    args.batch_dir.mkdir(parents=True, exist_ok=True)
    if batch_path(args.batch_dir).exists():
        sys.exit(f"{batch_path(args.batch_dir)} already exists.")
    for sub in ("source", "proofs"):
        (args.batch_dir / sub).mkdir(exist_ok=True)
    save_batch(args.batch_dir, {"batch": args.batch_dir.name, "month": args.month or month_folder(), "rows": []})
    print(f"Created {batch_path(args.batch_dir)}. Add rows to \"rows\" by hand or with fetch. An example row:")
    print(json.dumps(example_row(), ensure_ascii=False, indent=2))
    return 0


def cmd_prepare(args):
    need_ffmpeg()
    batch = load_batch(args.batch_dir)
    categories = sound_categories()
    refused, done = [], 0
    if len(batch["rows"]) > BATCH_SIZE_HINT:
        print(f"note: {len(batch['rows'])} rows; a review batch is about 50 sounds.")
    seen = {}
    for row in batch["rows"]:
        problems = gate(row, args.batch_dir, categories)
        if row["id"] in seen:
            problems.append(f"same id as row {seen[row['id']]} (same source_url); use another sound or a 'revision'")
        seen[row["id"]] = short(row)
        source = Path(row.get("source", ""))
        source = source if source.is_absolute() else args.batch_dir / source
        if not problems and not source.is_file():
            problems.append(f"source file {source} not found")
        title = (row.get("title") or {}).get("en", "?")
        if problems:
            refused.append((row, problems))
            print(f"REFUSED {short(row)} {title}")
            for p in problems:
                print(f"    - {p}")
            continue
        for w in warnings(row):
            print(f"warning {short(row)} {title}: {w}")
        key = f"sounds/{batch['month']}/{row['id']}.m4a"
        if row.get("status") == "loaded":
            print(f"skip    {short(row)} {title}: already loaded (keys are immutable; add a 'revision' for a changed file)")
            continue
        if row.get("processed") and not args.force:
            print(f"skip    {short(row)} {title}: already prepared (--force to redo; approval is reset)")
            done += 1
            continue
        if args.dry_run:
            trim = row.get("trim") or {}
            try:
                probed = probe_ms(source)
            except subprocess.CalledProcessError:
                refused.append((row, [f"ffprobe cannot read {source.name}"]))
                print(f"REFUSED {short(row)} {title}: ffprobe cannot read {source.name}")
                continue
            if batch.get("mode") == "waveform-only":
                print(f"PLAN    {short(row)} {title}: {source.name} ({probed} ms) is the live file; {BUCKETS}-bucket waveform only "
                      f"-> waveforms/{batch['month']}/{row['id']}.json")
            else:
                print(f"PLAN    {short(row)} {title}: {source.name}, probe {probed} ms; trim {trim or 'silence only'} -> "
                      f"-16 LUFS / -1 dBTP -> AAC 128 kbps .m4a -> {BUCKETS}-bucket waveform -> {key}")
            done += 1
            continue
        try:
            record = process_waveform_only(row, args.batch_dir) if batch.get("mode") == "waveform-only" else process(row, args.batch_dir)
            bad = processing_problems(record, batch.get("mode") == "waveform-only")
        except subprocess.CalledProcessError as error:
            detail = (error.stderr or b"")
            detail = detail.decode("utf-8", "replace") if isinstance(detail, bytes) else detail
            record, bad = None, [f"ffmpeg could not process {source.name}: {(detail.strip().splitlines() or ['unknown error'])[-1][:200]}"]
        except (ValueError, OSError) as error:
            record, bad = None, [str(error)]
        if bad:
            shutil.rmtree(args.batch_dir / "out" / ".tmp", ignore_errors=True)
            for leftover in (args.batch_dir / "out").glob(f"{row['id']}.*"):
                leftover.unlink()  # a refused sound leaves no files behind
            refused.append((row, bad))
            row.pop("processed", None)  # a refused re-run must not leave an approved row pointing at deleted files
            row.pop("approvedHash", None)
            row["status"] = "refused"
            print(f"REFUSED {short(row)} {title}")
            for p in bad:
                print(f"    - {p}")
            continue
        row["processed"] = record
        row["status"] = "pending"
        row.pop("approvedHash", None)
        done += 1
        print(f"ok      {short(row)} {title}: {record['durationMs']} ms, {record['lufs']:.1f} LUFS, "
              f"{record['truePeakDbtp']:.1f} dBTP, {record['bytes']} bytes")
    if args.dry_run:
        print(f"dry run: {done} planned, {len(refused)} refused; nothing written")
    else:
        save_batch(args.batch_dir, batch)
        write_review(args.batch_dir, batch, refused)
        print(f"{done} prepared, {len(refused)} refused. Review table: {args.batch_dir / 'review.md'}")
    return 1 if refused else 0


def ids_arg(value):
    return [v.strip().lower() for v in value.split(",") if v.strip()] if value else []


def pick(batch, prefixes):
    chosen = []
    for prefix in prefixes:
        found = [r for r in batch["rows"] if r["id"].startswith(prefix)]
        if len(found) != 1:
            sys.exit(f"'{prefix}' matches {len(found)} rows; give a longer id prefix.")
        chosen.append(found[0])
    return chosen


def cmd_approve(args):
    if not args.all and not args.ids and not args.reject:
        sys.exit("Say what to approve: --all, --ids <id8>,..., or --reject <id8>,...")
    batch = load_batch(args.batch_dir)
    categories = sound_categories()
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    targets = [r for r in batch["rows"] if r.get("processed") and r.get("status") != "loaded"] if args.all else pick(batch, ids_arg(args.ids))
    skipped = 0
    for row in targets:
        problems = gate(row, args.batch_dir, categories) if row.get("processed") else ["not prepared yet"]
        if problems:
            skipped += 1
            print(f"NOT approved {short(row)}: {'; '.join(problems)}")
            continue
        row["status"], row["approvedBy"], row["approvedAt"] = "approved", args.by, now
        row["approvedHash"] = canonical_hash(row)
        print(f"approved {short(row)} {row['title']['en']}")
    for row in pick(batch, ids_arg(args.reject)):
        row["status"] = "rejected"
        row.pop("approvedHash", None)
        print(f"rejected {short(row)} {row['title']['en']}")
    save_batch(args.batch_dir, batch)
    write_review(args.batch_dir, batch, [])
    return 1 if skipped else 0


def sql_text(text):
    return "'" + str(text).replace("'", "''") + "'"


def row_sql(row, batch):
    title = json.dumps(row["title"], ensure_ascii=False, separators=(",", ":"))
    tags = "array[" + ", ".join(sql_text(t) for t in row.get("tags", [])) + "]::text[]"
    regions = "array[" + ", ".join(sql_text(r) for r in row.get("regions", ["global"])) + "]::text[]"
    credit = sql_text(row["credit"].strip()) if row.get("credit") else "null"
    month, rid = batch["month"], row["id"]
    file_key, wave_key = f"sounds/{month}/{rid}.m4a", f"waveforms/{month}/{rid}.json"
    if batch.get("mode") == "waveform-only":
        return f"update public.sounds set waveform_path = {sql_text(wave_key)} where id = {sql_text(rid)} and waveform_path is null;"
    return (f"  ({sql_text(rid)}, {sql_text(title)}::jsonb, {sql_text(row['category_id'])}, {regions}, {tags},\n"
            f"   {sql_text(file_key)}, {int(row['processed']['durationMs'])}, {sql_text(wave_key)},\n"
            f"   {sql_text(row['source_url'])}, {sql_text(row['license_type'])}, {sql_text(row['license_proof'])}, {credit}, "
            f"{sql_text(row['added_by'])})")


def write_sql(batch, rows):
    if batch.get("mode") == "waveform-only":
        return (f"-- Waveforms for sounds that are already live (batch {batch['batch']}, P3-12): run after the waveform files are uploaded.\n"
                "-- Idempotent: only fills a null waveform_path.\n" + "\n".join(row_sql(r, batch) for r in rows) + "\n")
    head = [f"-- Sounds batch {batch['batch']} (P3-12): {len(rows)} owner-approved rows, generated by scripts/media/intake_sounds.py.",
            "-- Apply AFTER the files are uploaded (load does that first): the rows are active as soon as they exist.",
            "-- Idempotent: re-running changes nothing (on conflict do nothing). Takedown: update public.sounds set is_active = false where id = '...';",
            "insert into public.sounds (id, title, category_id, regions, tags, file_path, duration_ms, waveform_path,",
            "                           source_url, license_type, license_proof, credit, added_by)", "values"]
    return "\n".join(head) + "\n" + ",\n".join(row_sql(r, batch) for r in rows) + "\non conflict (id) do nothing;\n"


def load_uploader():
    spec = importlib.util.spec_from_file_location("upload_media", UPLOAD_PY)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def cmd_load(args):
    batch = load_batch(args.batch_dir)
    categories = sound_categories()
    up = load_uploader()
    env = up.read_env(args.env_file)
    base_url, secret = env.get("SUPABASE_URL", "").rstrip("/"), env.get("SUPABASE_SECRET_KEY", "")
    if not args.dry_run and (not up.SUPABASE_URL_PATTERN.match(base_url) or not secret):
        sys.exit("Set SUPABASE_URL (https://<project-ref>.supabase.co) and SUPABASE_SECRET_KEY in .env (docs/CREDENTIALS.md).")
    base_url = base_url or "https://<project-ref>.supabase.co"
    upload_args = argparse.Namespace(dry_run=args.dry_run, cache_control=up.DEFAULT_CACHE_CONTROL)
    approved = [r for r in batch["rows"] if r.get("status") in ("approved", "loaded")]
    if not approved:
        sys.exit("No approved rows. Review review.md, then run approve.")
    waveform_only = batch.get("mode") == "waveform-only"
    failures, passing = 0, []  # only rows that pass every check in this run can reach the SQL
    for row in approved:
        title = row["title"]["en"]
        problems = gate(row, args.batch_dir, categories)
        p = row.get("processed") if isinstance(row.get("processed"), dict) else {}
        files = []
        if not problems:
            if not waveform_only:
                files.append((processed_path(args.batch_dir, p.get("file")), f"sounds/{batch['month']}/{row['id']}.m4a", p.get("sha256")))
            files.append((processed_path(args.batch_dir, p.get("waveform")), f"waveforms/{batch['month']}/{row['id']}.json", p.get("waveformSha256")))
            for path, _, sha in files:
                if path is None or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != sha:
                    problems.append(f"{path.name if path else 'a processed file'} is missing or changed since prepare")
        if row.get("approvedHash") != canonical_hash(row):
            problems.append("the row changed after it was approved; approve it again")
        if problems:
            failures += 1
            print(f"REFUSED {short(row)} {title}: {'; '.join(problems)}")
            continue
        if row.get("status") == "loaded":
            print(f"already loaded {short(row)} {title}")
            passing.append(row)
            continue
        ok = True
        for path, key, _ in files:  # the audio first, so a row never points at a missing sound
            up.check_key(key, path)
            ok = ok and up.upload(path, key, upload_args, base_url, secret)
        if not ok:
            failures += 1
            continue
        passing.append(row)
        if not args.dry_run:
            row["status"] = "loaded"
            save_batch(args.batch_dir, batch)  # an interrupted run keeps what already went up
    if args.dry_run:
        print(f"\ndry run: {len(passing)} approved rows planned; nothing uploaded or written. The SQL would be:\n")
        print(write_sql(batch, passing) if passing else "(no rows)")
        return 1 if failures else 0
    if passing:
        (args.batch_dir / "load.sql").write_text(write_sql(batch, passing), encoding="utf-8")
        print(f"\n{len(passing)} rows in {args.batch_dir / 'load.sql'}. Apply it in the Supabase SQL editor of project "
              "drnhpnixnewqjfmrchrr, or copy it to supabase/migrations/<yyyymmddhhmm>_sounds_" + batch["batch"] + ".sql.")
    return 1 if failures else 0


def cmd_starters(args):
    """The 10 live starter sounds as a waveform-only batch: it makes their waveform files from the live audio and an update for sounds.waveform_path."""
    spec = importlib.util.spec_from_file_location("starter_seed_sql", ROOT / "scripts/media/starter_seed_sql.py")
    seed = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(seed)
    manifest = json.loads((ROOT / "content/starter-sounds/manifest.json").read_text(encoding="utf-8"))
    rows = []
    for s in manifest["sounds"]:
        category, tags = seed.CATALOG[s["file"].removesuffix(".m4a")]
        rows.append({"id": s["id"], "source": str(ROOT / "content/starter-sounds" / s["file"]), "title": s["title"],
                     "category_id": category, "regions": ["global"], "tags": tags, "source_url": s["sourceUrl"],
                     "license_type": s["licenseType"], "license_url": s["licenseUrl"], "license_proof": s["licenseProof"],
                     "credit": s["credit"], "added_by": "owner", "expectSha256": s["sha256Output"]})
    args.batch_dir.mkdir(parents=True, exist_ok=True)
    save_batch(args.batch_dir, {"batch": "starters-waveforms", "month": seed.KEY_YEAR_MONTH, "mode": "waveform-only", "rows": rows})
    print(f"Wrote {batch_path(args.batch_dir)} with {len(rows)} starter rows in waveform-only mode (the live audio files are "
          "not touched). Next: prepare, approve --all, load --dry-run.")
    return 0


# ---------------------------------------------------------------- Freesound

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(req.full_url, code, f"redirect to {newurl} refused", headers, fp)


OPENER = urllib.request.build_opener(NoRedirect)


def http_get(url, headers=None):
    req = urllib.request.Request(url, headers=headers or {})
    with OPENER.open(req, timeout=60) as response:
        return response.read()


def freesound_id(text):
    match = re.search(r"/sounds/(\d+)", text) or re.match(r"^(\d+)$", text.strip())
    if not match:
        sys.exit(f"'{text}' is not a Freesound sound URL or id.")
    return match.group(1)


def clean_title(name):
    name = re.sub(r"\.(wav|mp3|flac|aif+|ogg|m4a)$", "", name, flags=re.I).replace("_", " ").strip()
    return name[:1].upper() + name[1:]


def cmd_fetch(args):
    """Adds Freesound sounds to the batch: API metadata, licence check, HQ preview download, proof snapshot."""
    batch = load_batch(args.batch_dir)
    if args.category not in sound_categories():
        sys.exit(f"--category must be one of {', '.join(sorted(sound_categories()))}")
    ids = [freesound_id(s) for s in args.sounds]
    if args.dry_run:
        for fid in ids:
            print(f"PLAN https://freesound.org/apiv2/sounds/{fid}/ (token from .env), refuse unless CC0 or CC-BY, "
                  f"download the HQ preview to source/{fid}.mp3, write proofs/{fid}.md, add a row to batch.json")
        print("dry run: nothing requested or written")
        return 0
    key = read_env_names(args.env_file, {"FREESOUND_API_KEY"}).get("FREESOUND_API_KEY", "")
    if not key:
        sys.exit("Set FREESOUND_API_KEY in .env (owner only; docs/CREDENTIALS.md).")
    (args.batch_dir / "source").mkdir(exist_ok=True)
    (args.batch_dir / "proofs").mkdir(exist_ok=True)
    failures = 0
    for fid in ids:
        try:
            added = fetch_one(args, batch, fid, key)
        except (urllib.error.URLError, OSError, ValueError, KeyError, TypeError, AttributeError) as error:
            failures += 1
            reason = f"missing field {error}" if isinstance(error, KeyError) else str(getattr(error, "code", "") or "")
            print(f"FAILED {fid}: {type(error).__name__} {reason}".rstrip())
            continue
        if added is False:
            failures += 1
        elif added:
            save_batch(args.batch_dir, batch)  # one sound at a time: a later failure loses nothing
            time.sleep(1.1)  # Freesound's limit is 60 requests a minute
    return 1 if failures else 0


def fetch_one(args, batch, fid, key):
    """Returns True when a row was added, None when skipped, False when refused."""
    fields = "id,name,username,license,url,duration,tags,previews,description"
    # the key goes in a header, never in a URL that an error message could print
    meta = json.loads(http_get(f"https://freesound.org/apiv2/sounds/{fid}/?fields={fields}", {"Authorization": f"Token {key}"}))
    license_type = license_url_type(meta.get("license", ""))
    if license_type is None:
        print(f"REFUSED {fid} {meta.get('name')}: license {meta.get('license')} is not CC0 or CC-BY")
        return False
    title = clean_title(meta["name"])
    source_url = meta["url"].rstrip("/") + "/"
    if any(r.get("source_url") == source_url for r in batch["rows"]):
        print(f"skip {fid}: already in the batch")
        return None
    preview_url = meta["previews"]["preview-hq-mp3"]
    if not isinstance(preview_url, str) or not HTTPS_URL.match(preview_url):
        raise ValueError("the preview URL is not https")
    preview = http_get(preview_url)  # CDN host: no token is sent
    (args.batch_dir / "source" / f"{fid}.mp3").write_bytes(preview)
    read_at = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    proof = args.batch_dir / "proofs" / f"{fid}.md"
    proof.write_text(
        f"# License snapshot: {meta['name']} (Freesound {fid})\n\n"
        f"Text snapshot of the Freesound API record, read at {read_at} (UTC).\n\n"
        f"| Field | Value |\n| --- | --- |\n| Page URL | {source_url} |\n| Author | {meta['username']} |\n"
        f"| License URL | {meta['license']} |\n| Duration | {meta['duration']} s |\n"
        f"| Preview SHA-256 | {hashlib.sha256(preview).hexdigest()} |\n\n"
        f"Description (verbatim):\n\n> {str(meta.get('description', '')).replace(chr(10), ' ')}\n", encoding="utf-8")
    version = re.search(r"/by/(\d\.\d)", meta["license"])
    credit = (f"{title} by {meta['username']}, Freesound (CC BY {version.group(1) if version else ''}, edited)".replace("  ", " ")
              if license_type == "CC-BY" else None)
    batch["rows"].append({
        "source": f"source/{fid}.mp3", "title": {"en": title, "id": "", "es": "", "pt": "", "hi": ""},
        "category_id": args.category, "regions": ["global"], "tags": [t.lower() for t in meta.get("tags", [])][:6],
        "source_url": source_url, "license_type": license_type, "license_url": meta["license"],
        "license_proof": str(proof.resolve().relative_to(ROOT)), "credit": credit,
        "added_by": args.added_by, "trim": {"start": 0, "duration": None, "fade_in": 0, "fade_out": 0}})
    print(f"added {fid} {title} ({license_type}); fill the translated titles, then run prepare")
    return True


# ---------------------------------------------------------------- entry

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    sub = parser.add_subparsers(dest="command", required=True)

    def add(name, fn, **kw):
        p = sub.add_parser(name, **kw)
        p.add_argument("batch_dir", type=Path)
        p.set_defaults(fn=fn)
        return p

    p = add("init", cmd_init)
    p.add_argument("--month", help="key folder yyyy/mm (default: this month, UTC)")
    p = add("fetch", cmd_fetch)
    p.add_argument("sounds", nargs="+")
    p.add_argument("--category", required=True)
    p.add_argument("--added-by", default="owner")
    p.add_argument("--dry-run", action="store_true")
    p = add("prepare", cmd_prepare)
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--force", action="store_true")
    p = add("approve", cmd_approve)
    p.add_argument("--all", action="store_true")
    p.add_argument("--ids")
    p.add_argument("--reject")
    p.add_argument("--by", default="owner")
    p = add("load", cmd_load)
    p.add_argument("--dry-run", action="store_true")
    add("starters", cmd_starters)
    args = parser.parse_args()
    args.batch_dir = args.batch_dir.resolve()
    if not inside(args.batch_dir, ROOT):
        sys.exit(f"{args.batch_dir} is outside the repo; use content/intake/<name>.")
    return args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
