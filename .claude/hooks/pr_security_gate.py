#!/usr/bin/env python3
"""PreToolUse gate: no pull request opens until the security checks pass (owner request, 8 Oct 2026).

Registered in .claude/settings.json for Bash and the GitHub MCP create_pull_request tool. Every other
tool call passes straight through. A pull request is allowed only when:

1. scripts/check-secrets.sh finds nothing in the tracked files or in any commit since the base branch
   (the repo is public, so a key deleted in a later commit is still exposed);
2. the checked-out commit is pushed, so what was scanned is what the pull request shows;
3. the security-reviewer agent wrote a PASS verdict for exactly this commit to
   <git dir>/memix-security-review.json (never committed).

Anything that can't be checked blocks, so a broken gate never waves a pull request through.
Exit 2 blocks the tool call and shows the message to Claude.
"""
import json
import os
import re
import subprocess
import sys

BASE_BRANCH = os.environ.get("MEMIX_PR_BASE", "main")
VERDICT_FILE = "memix-security-review.json"
# Only where a command starts: line start, after ; & | ( $( or a quote that opens `bash -c "..."`, with
# optional VAR=value, env, command or sudo prefixes. Words in the middle of a commit message or an echo
# don't count; a message that starts with the command itself does, and blocking it is the safe mistake.
COMMAND_START = r"(?:^|[;&|(\n'\"]|\$\()\s*(?:(?:\w+=\S*|env|command|sudo)\s+)*"
PR_COMMAND = re.compile(
    COMMAND_START
    + r"(?:gh\s+pr\s+create\b"
    r"|hub\s+pull-request\b"
    r"|gh\s+api\b(?=[^\n]*/pulls\b)(?=[^\n]*(?:-X\s*POST|--method\s+POST|\s-[fF]\s))"
    r"|(?:curl|wget|http|https)\b(?=[^\n]*api\.github\.com/repos/[^\s'\"]+/pulls\b)"
    r"(?=[^\n]*(?:-X\s*POST|--request\s+POST|\s-d\s|--data|--json|\bPOST\b)))"
)


def opens_pull_request(event: dict) -> bool:
    tool = event.get("tool_name") or ""
    if tool.endswith("create_pull_request"):
        return True
    if tool == "Bash":
        command = (event.get("tool_input") or {}).get("command") or ""
        return bool(PR_COMMAND.search(command))
    return False


def git(*args: str, timeout: int = 30) -> str:
    return subprocess.run(
        ["git", *args], check=True, capture_output=True, text=True, timeout=timeout
    ).stdout.strip()


def block(reason: str) -> int:
    print(f"Pull request blocked by the security gate (.claude/hooks/pr_security_gate.py).\n{reason}", file=sys.stderr)
    return 2


def check(event: dict) -> int:
    root = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    os.chdir(root)

    branch = git("rev-parse", "--abbrev-ref", "HEAD")
    wanted_head = (event.get("tool_input") or {}).get("head")
    if wanted_head and wanted_head.split(":")[-1] != branch:
        return block(
            f"The pull request's head is '{wanted_head}' but '{branch}' is checked out, so the scan would "
            f"cover the wrong code. Check out '{wanted_head}', then run the security-reviewer agent."
        )

    try:
        git("fetch", "--quiet", "origin", BASE_BRANCH, timeout=60)
    except (subprocess.SubprocessError, OSError):
        pass  # offline: compare against the last fetched base
    base = f"origin/{BASE_BRANCH}"

    for args in ([], ["--range", base]):
        scan = subprocess.run(
            ["sh", "scripts/check-secrets.sh", *args], capture_output=True, text=True, timeout=120
        )
        if scan.returncode != 0:
            where = "the branch history since " + base if args else "the tracked files"
            return block(f"scripts/check-secrets.sh found a credential in {where}:\n{scan.stderr.strip()}")

    head = git("rev-parse", "HEAD")
    try:
        upstream = git("rev-parse", "@{upstream}")
    except subprocess.CalledProcessError:
        return block(f"'{branch}' has no upstream. Push it first so the scanned commit is the pull request's head.")
    if upstream != head:
        return block("The checked-out commit isn't the pushed one. Push (or pull) first, then run the security-reviewer agent.")

    verdict_path = os.path.join(git("rev-parse", "--absolute-git-dir"), VERDICT_FILE)
    try:
        with open(verdict_path, encoding="utf-8") as file:
            verdict = json.load(file)
    except (OSError, ValueError):
        return block(
            f"No security review for this commit. Run the security-reviewer agent on {head[:7]}; it writes "
            f"{verdict_path} with PASS or BLOCK."
        )
    if verdict.get("head") != head:
        return block(
            f"The last security review covered {str(verdict.get('head'))[:7]}, not {head[:7]}. "
            "Run the security-reviewer agent again on the current commit."
        )
    if verdict.get("verdict") != "PASS":
        summary = verdict.get("summary") or "see its report"
        return block(f"The security-reviewer agent found something crucial ({summary}). Fix it and run the review again.")
    return 0


def main() -> int:
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return 0  # not a tool event we understand; never block ordinary work on a parse problem
    if not opens_pull_request(event):
        return 0
    try:
        return check(event)
    except Exception as error:  # noqa: BLE001 - any failure must block, not allow
        return block(f"The gate couldn't run ({type(error).__name__}: {error}), so the pull request stays blocked.")


if __name__ == "__main__":
    sys.exit(main())
