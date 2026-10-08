#!/bin/sh
# Fails when a key, token, private key or credential file is about to enter git (docs/CREDENTIALS.md).
#   scripts/check-secrets.sh --staged          checks what's staged (pre-commit hook)
#   scripts/check-secrets.sh                   checks every tracked file (CI)
#   scripts/check-secrets.sh --range <base>    checks every commit since <base> (the PR gate and PR CI):
#                                              a key added in one commit and deleted in the next is still in
#                                              the history, and this repo is public
set -eu
cd "$(git rev-parse --show-toplevel)"

content='AIza[0-9A-Za-z_-]{35}|sb_secret_[A-Za-z0-9_-]{10,}|sb_publishable_[A-Za-z0-9_-]{10,}|eyJhbGciOi[A-Za-z0-9_-]{20,}\.|-----BEGIN [A-Z ]*PRIVATE KEY-----|ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}|xox[abp]-[A-Za-z0-9-]{10,}|AKIA[0-9A-Z]{16}|GOCSPX-[A-Za-z0-9_-]{20,}|(sk|rk)_live_[A-Za-z0-9]{20,}|sk-ant-[A-Za-z0-9_-]{20,}|sk-proj-[A-Za-z0-9_-]{20,}|glpat-[A-Za-z0-9_-]{20,}|hooks\.slack\.com/services/[A-Za-z0-9/]{20,}|discord(app)?\.com/api/webhooks/[0-9]+/|"type"[[:space:]]*:[[:space:]]*"service_account"|(SECRET_KEY|SECRET_ACCESS_KEY|DB_PASSWORD|ACCESS_TOKEN|API_TOKEN|API_KEY|KEYSTORE_PASSWORD|KEY_PASSWORD|storePassword|keyPassword)[[:space:]]*[=:][[:space:]]*["'"'"']?[A-Za-z0-9/+_-]{12,}'
names='(^|/)(google-services\.json|GoogleService-Info\.plist|local\.properties|keystore\.properties|signing\.properties|\.env(\.[^/]+)?|\.dev\.vars)$|\.(jks|keystore|p8|p12|pem|key|cer|certSigningRequest|mobileprovision|provisionprofile)$|service[-_]account.*\.json$|-firebase-adminsdk-.*\.json$|(^|/)client_secret[^/]*\.json$'

# Report file:line (or commit and file) only, never the matched text, so a real key doesn't end up in logs.
hits=""
if [ "${1:-}" = "--staged" ]; then
    files=$(git diff --cached --name-only --diff-filter=ACMR)
    for f in $files; do
        lines=$(git show ":$f" 2>/dev/null | grep -nIE "$content" | cut -d: -f1 | tr '\n' ' ' || true)
        [ -n "$lines" ] && hits="$hits$f: line $lines\n"
    done
elif [ "${1:-}" = "--range" ]; then
    base="${2:?usage: scripts/check-secrets.sh --range <base>}"
    range="$(git merge-base "$base" HEAD)..HEAD"
    # Every path any commit in the range added or changed, even if a later commit removed it again.
    files=$(git log --format= --name-only --diff-filter=ACMR "$range" | sort -u)
    # Added lines only; ENVIRON keeps awk from rewriting the pattern's backslashes.
    hits=$(git log -p --format='@@commit %h' --diff-filter=ACMR "$range" -- . ':!.env.example' \
        | PATTERN="$content" awk '
            /^@@commit / { commit = $2; next }
            /^\+\+\+ b\// { file = substr($0, 7); next }
            /^\+/ && !/^\+\+\+ / && $0 ~ ENVIRON["PATTERN"] { print "commit " commit ": " file }' \
        | sort -u | sed 's/$/\\n/' | tr -d '\n')
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
    [ "${1:-}" = "--range" ] && echo "It is in the branch history: rotate the credential first, then ask the owner before rewriting history." >&2
    exit 1
fi
