#!/usr/bin/env python3
"""Uploads catalog media to the public Supabase Storage bucket 'media' under a versioned key (P3-13).

    python3 scripts/media/upload_media.py --dry-run --starters          # plan only, nothing is sent
    python3 scripts/media/upload_media.py --starters                    # the 10 starter sounds, keys as in the seed migration
    python3 scripts/media/upload_media.py <file> <key> [--dry-run]      # one file, e.g. sounds/2026/12/<uuid>.m4a

Rules
- Reads SUPABASE_URL and SUPABASE_SECRET_KEY from the repo's .env file only (never from the environment, never from
  arguments), and never prints the key. --dry-run works without a .env.
- Keys are immutable: an upload never overwrites (x-upsert: false). A changed file gets a new key and a new row value.
- Every file is sent with Cache-Control: public, max-age=31536000 (one year). Override with --cache-control, for
  example to try adding ", immutable"; the script then reads the public URL back and prints the header Storage returned.
- The publishable key can read these files and cannot write: see supabase/migrations/20261110100000_media_bucket.sql.
Python 3.9 or newer, standard library only.
"""
import argparse
import hashlib
import importlib.util
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUCKET = "media"
DEFAULT_CACHE_CONTROL = "public, max-age=31536000"
MAX_BYTES = 10 * 1024 * 1024  # the bucket's file_size_limit
KEY_PATTERN = re.compile(r"^(sounds|waveforms|templates|previews|stickers)/[A-Za-z0-9][A-Za-z0-9._/-]*$")
CONTENT_TYPES = {  # must stay inside the bucket's allowed_mime_types
    ".m4a": "audio/mp4", ".aac": "audio/aac", ".mp3": "audio/mpeg", ".json": "application/json",
    ".webp": "image/webp", ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".mp4": "video/mp4",
}


def read_env(path):
    """Only the two names this script needs; a quoted or unquoted KEY=value per line."""
    wanted = {"SUPABASE_URL", "SUPABASE_SECRET_KEY"}
    values = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, _, value = line.partition("=")
        name = name.strip()
        if name in wanted:
            values[name] = value.strip().strip("'\"")
    return values


SUPABASE_URL_PATTERN = re.compile(r"^https://[a-z0-9]{20}\.supabase\.co$")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """A redirect would re-send the secret key to wherever it points; refuse it instead."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise urllib.error.HTTPError(req.full_url, code, f"redirect to {newurl} refused", headers, fp)


OPENER = urllib.request.build_opener(NoRedirect)


def check_key(key, path=None):
    bad_ext = path is not None and Path(key).suffix.lower() != Path(path).suffix.lower()
    if not KEY_PATTERN.match(key) or ".." in key or "//" in key or "/./" in key or key.endswith("/") or bad_ext:
        sys.exit(f"Key '{key}' is not allowed: use sounds|waveforms|templates|previews|stickers/<path> with letters, digits, . _ - /, ending in the file's own extension")


def starter_items():
    """(file, key) for the 10 starter sounds, using the key rule the seed migration was generated with."""
    spec = importlib.util.spec_from_file_location("starter_seed_sql", ROOT / "scripts/media/starter_seed_sql.py")
    seed = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(seed)
    manifest = json.loads((ROOT / "content/starter-sounds/manifest.json").read_text(encoding="utf-8"))
    items = []
    for s in manifest["sounds"]:
        path = ROOT / "content/starter-sounds" / s["file"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != s["sha256Output"]:
            sys.exit(f"{path.name} does not match sha256Output in manifest.json; refusing to upload a stale file.")
        items.append((path, f"sounds/{seed.KEY_YEAR_MONTH}/{s['id']}.m4a"))
    return items


def request(url, method, headers, body=None):
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with OPENER.open(req, timeout=60) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.headers or {}, error.read() if error.fp else b""
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        return 0, {}, str(getattr(error, "reason", error)).encode()


def upload(path, key, args, base_url, secret):
    data = path.read_bytes()
    content_type = CONTENT_TYPES.get(path.suffix.lower())
    if content_type is None:
        sys.exit(f"No content type for '{path.suffix}'; add it to CONTENT_TYPES and to the bucket's allowed_mime_types.")
    if len(data) > MAX_BYTES:
        sys.exit(f"{path.name} is {len(data)} bytes; the bucket limit is {MAX_BYTES}.")
    public_url = f"{base_url}/storage/v1/object/public/{BUCKET}/{key}"
    print(f"{'DRY RUN ' if args.dry_run else ''}{key}  {len(data)} bytes  {content_type}  sha256 {hashlib.sha256(data).hexdigest()[:16]}")
    print(f"    Cache-Control: {args.cache_control}")
    print(f"    public URL: {public_url}")
    if args.dry_run:
        return True
    headers = {"Authorization": f"Bearer {secret}", "apikey": secret, "Content-Type": content_type,
               "Cache-Control": args.cache_control, "x-upsert": "false"}
    status, _, body = request(f"{base_url}/storage/v1/object/{BUCKET}/{key}", "POST", headers, data)
    if status == 200:
        print("    uploaded")
    elif status in (400, 409) and b"exist" in body.lower():
        print("    already exists (keys are never overwritten); checking what is served")
    else:
        print(f"    FAILED: HTTP {status} {body[:300].decode('utf-8', 'replace')}")
        return False
    return verify(public_url, args.cache_control)


def verify(public_url, wanted):
    """HEAD the anonymous public URL, as the app and curl -I see it."""
    status, headers, _ = request(public_url, "HEAD", {})
    served = headers.get("Cache-Control") if headers else None
    print(f"    served: HTTP {status}, Cache-Control: {served}, Content-Type: {headers.get('Content-Type')}")
    ok = status == 200 and served is not None and "max-age=31536000" in served
    if not ok:
        print(f"    WARNING: expected max-age=31536000 (sent '{wanted}'); the file is not cached for a year.")
    elif "immutable" not in served:
        print("    note: no 'immutable' token; public, max-age=31536000 on a versioned key is the accepted fallback.")
    return ok


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("file", nargs="?", type=Path)
    parser.add_argument("key", nargs="?")
    parser.add_argument("--starters", action="store_true", help="upload the 10 files in content/starter-sounds/")
    parser.add_argument("--dry-run", action="store_true", help="print what would be sent; send nothing")
    parser.add_argument("--cache-control", default=DEFAULT_CACHE_CONTROL)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()

    if args.starters == bool(args.file):
        parser.error("give either --starters or <file> <key>")
    items = starter_items() if args.starters else [(args.file, args.key)]
    for path, key in items:
        if key is None:
            parser.error("<key> is required with <file>")
        if not Path(path).is_file():
            sys.exit(f"{path} is not a file.")
        check_key(key, path)

    env = read_env(args.env_file)
    base_url = env.get("SUPABASE_URL", "").rstrip("/")
    secret = env.get("SUPABASE_SECRET_KEY", "")
    if not args.dry_run:
        if not SUPABASE_URL_PATTERN.match(base_url) or not secret:
            sys.exit("Set SUPABASE_URL (https://<project-ref>.supabase.co) and SUPABASE_SECRET_KEY in .env (docs/CREDENTIALS.md).")
    base_url = base_url or "https://<project-ref>.supabase.co"

    results = [upload(Path(path), key, args, base_url, secret) for path, key in items]
    print(f"{sum(results)} of {len(results)} {'planned' if args.dry_run else 'done'}")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
