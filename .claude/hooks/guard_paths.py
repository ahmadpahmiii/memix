#!/usr/bin/env python3
"""PreToolUse guard for Memix role agents.

Blocks Edit/Write/MultiEdit/NotebookEdit on any file outside the globs a role
may change, so the product manager, designer and QA stay inside their lanes.

Used from an agent's frontmatter hook:
    python3 "${CLAUDE_PROJECT_DIR}/.claude/hooks/guard_paths.py" <role> "<glob>" ["<glob>" ...]

Globs are relative to the project root and use fnmatch rules ("*" also
matches "/"), e.g. "docs/qa/*" or "*/src/*Test/*". Exit 2 blocks the tool call
and shows the message to the agent.
"""
import fnmatch
import json
import os
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print("guard_paths.py: usage: guard_paths.py <role> <glob> [<glob> ...]", file=sys.stderr)
        return 2
    role, globs = sys.argv[1], sys.argv[2:]

    try:
        event = json.load(sys.stdin)
    except json.JSONDecodeError:
        print(f"{role}: could not read the hook input, so the edit was blocked to be safe.", file=sys.stderr)
        return 2

    tool_input = event.get("tool_input") or {}
    target = tool_input.get("file_path") or tool_input.get("notebook_path")
    if not target:
        return 0  # not a file edit

    root = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    if not os.path.isabs(target):
        target = os.path.join(event.get("cwd") or root, target)
    rel = os.path.relpath(os.path.realpath(target), os.path.realpath(root)).replace(os.sep, "/")

    if not rel.startswith("../") and any(fnmatch.fnmatch(rel, g) for g in globs):
        return 0

    print(
        f"Blocked: {role} may not edit '{rel}'. This role may only change: {', '.join(globs)}. "
        "Write the change up as a request for the agent that owns this file (see CLAUDE.md → Team) "
        "and include it in your report instead.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
