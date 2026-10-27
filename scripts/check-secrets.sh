#!/bin/sh
# Fails when a key, token, private key or credential file is about to enter git (docs/CREDENTIALS.md).
#   scripts/check-secrets.sh --staged   checks what's staged (pre-commit hook)
#   scripts/check-secrets.sh            checks every tracked file (CI)
set -eu
cd "$(git rev-parse --show-toplevel)"

content='AIza[0-9A-Za-z_-]{35}|sb_secret_[A-Za-z0-9_-]{10,}|sb_publishable_[A-Za-z0-9_-]{10,}|eyJhbGciOi[A-Za-z0-9_-]{20,}\.|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}|xox[abp]-[A-Za-z0-9-]{10,}|(SECRET_KEY|SECRET_ACCESS_KEY|DB_PASSWORD|ACCESS_TOKEN|API_TOKEN|API_KEY|KEYSTORE_PASSWORD|KEY_PASSWORD|storePassword|keyPassword)[[:space:]]*[=:][[:space:]]*["'"'"']?[A-Za-z0-9/+_-]{12,}'
names='(^|/)(google-services\.json|GoogleService-Info\.plist|local\.properties|keystore\.properties|signing\.properties|\.env(\.[^/]+)?|\.dev\.vars)$|\.(jks|keystore|p8|p12|pem|key|mobileprovision|provisionprofile)$|service[-_]account.*\.json$|-firebase-adminsdk-.*\.json$'

# Report file:line only, never the matched text, so a real key doesn't end up in CI logs.
hits=""
if [ "${1:-}" = "--staged" ]; then
    files=$(git diff --cached --name-only --diff-filter=ACMR)
    for f in $files; do
        lines=$(git show ":$f" 2>/dev/null | grep -nIE "$content" | cut -d: -f1 | tr '\n' ' ' || true)
        [ -n "$lines" ] && hits="$hits$f: line $lines\n"
    done
else
    files=$(git ls-files)
    hits=$(git grep -nIE "$content" -- . ':!.env.example' | cut -d: -f1,2 || true)
fi

bad_files=$(printf '%s\n' "$files" | grep -E "$names" | grep -v '^\.env\.example$' || true)
if [ -n "$bad_files" ] || [ -n "$hits" ]; then
    echo "Blocked: this looks like a credential (see docs/CREDENTIALS.md)." >&2
    [ -n "$bad_files" ] && printf 'File: %s\n' $bad_files >&2
    [ -n "$hits" ] && printf "$hits\n" >&2
    echo "Move it to local.properties, .env or a CI secret. If it's a false alarm, adjust the pattern in scripts/check-secrets.sh." >&2
    exit 1
fi
