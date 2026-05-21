#!/usr/bin/env python3
"""Migrate android.util.Log calls to KBLog in KidBox Android."""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src")
SKIP_FILES = {"KBLog.kt"}

METHOD_MAP = {
    "d": "debug",
    "v": "debug",
    "i": "info",
    "w": "warning",
    "e": "error",
    "wtf": "error",
}

KBLOG_IMPORT = "import it.vittorioscocca.kidbox.util.KBLog"


def category_for_path(filepath: str) -> str:
    p = filepath.replace("\\", "/")
    if "/notifications/" in p:
        return "app"
    if "/data/sync/" in p or "/.sync." in p:
        return "sync"
    if "/data/crypto/" in p:
        return "crypto"
    if "/data/remote/auth/" in p or "/ui/screens/auth/" in p:
        return "auth"
    if (
        "/data/remote/ai/" in p
        or "/data/health/ai/" in p
        or "/ui/screens/ai/" in p
        or "/health/ai/" in p
    ):
        return "ai"
    if (
        "/security/" in p
        or "/passwords/" in p
        or "/feature/passwords/" in p
    ):
        return "security"
    if "/data/local/" in p:
        return "persistence"
    if "/ui/" in p:
        return "ui"
    if "/data/" in p:
        return "data"
    return "app"


def find_log_calls(text: str) -> list[tuple[int, int, str, str]]:
    """Return list of (start, end, method, full_match) for each Log call."""
    pattern = re.compile(r"(?:android\.util\.)?Log\.(d|i|w|e|v|wtf)\s*\(", re.MULTILINE)
    results = []
    for m in pattern.finditer(text):
        method = m.group(1)
        i = m.end()
        depth = 1
        while i < len(text) and depth > 0:
            ch = text[i]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            i += 1
        results.append((m.start(), i, method, text[m.start() : i]))
    return results


def split_args(args: str) -> list[str]:
    parts: list[str] = []
    current: list[str] = []
    depth = 0
    in_string = False
    string_char = ""
    escape = False
    i = 0
    while i < len(args):
        ch = args[i]
        if escape:
            current.append(ch)
            escape = False
            i += 1
            continue
        if in_string:
            current.append(ch)
            if ch == "\\":
                escape = True
            elif ch == string_char:
                in_string = False
            i += 1
            continue
        if ch in "\"'":
            in_string = True
            string_char = ch
            current.append(ch)
            i += 1
            continue
        if ch == "(":
            depth += 1
            current.append(ch)
            i += 1
            continue
        if ch == ")":
            depth -= 1
            current.append(ch)
            i += 1
            continue
        if ch == "," and depth == 0:
            parts.append("".join(current).strip())
            current = []
            i += 1
            continue
        current.append(ch)
        i += 1
    tail = "".join(current).strip()
    if tail:
        parts.append(tail)
    return parts


def convert_call(full_call: str, method: str, category: str) -> str:
    kb_method = METHOD_MAP[method]
    prefix = re.match(r"(?:android\.util\.)?Log\.\w+\s*\(", full_call)
    if not prefix:
        return full_call
    args_str = full_call[prefix.end() : -1]
    args = split_args(args_str)
    if not args:
        return full_call

    tag = args[0]
    if len(args) == 1:
        message = tag
        tag = '""'
    else:
        message = args[1]

    throwable = None
    if len(args) >= 3:
        throwable = args[2]
    elif kb_method == "error" and len(args) == 2:
        # Log.e(TAG, exception) when second arg looks like throwable
        if message.strip() and not (
            message.strip().startswith('"')
            or message.strip().startswith("'")
        ):
            throwable = message
            message = '""'

    tag_arg = ""
    if tag.strip() not in ('""', "''"):
        tag_arg = f", {tag}"

    if throwable:
        if kb_method == "warning":
            return f"KBLog.{category}.error({message}{tag_arg}, {throwable})"
        return f"KBLog.{category}.{kb_method}({message}{tag_arg}, {throwable})"

    if kb_method == "error":
        return f"KBLog.{category}.error({message}{tag_arg})"
    return f"KBLog.{category}.{kb_method}({message}{tag_arg})"


def migrate_file(path: str) -> bool:
    with open(path, encoding="utf-8") as f:
        text = f.read()

    if "KBLog.kt" in path:
        return False

    calls = find_log_calls(text)
    if not calls:
        # Still remove orphan import if present
        if "import android.util.Log" not in text:
            return False

    category = category_for_path(path)
    new_text = text
    for start, end, method, full_call in reversed(calls):
        replacement = convert_call(full_call, method, category)
        new_text = new_text[:start] + replacement + new_text[end:]

    new_text = re.sub(r"\nimport android\.util\.Log\n", "\n", new_text)
    new_text = new_text.replace("import android.util.Log\n", "")
    if KBLOG_IMPORT not in new_text:
        pkg_match = re.search(r"^package .+$", new_text, re.MULTILINE)
        if pkg_match:
            insert_at = pkg_match.end()
            new_text = new_text[:insert_at] + "\n\n" + KBLOG_IMPORT + new_text[insert_at:]
        else:
            new_text = KBLOG_IMPORT + "\n\n" + new_text

    if new_text != text:
        with open(path, "w", encoding="utf-8") as f:
            f.write(new_text)
        return True
    return False


def main() -> int:
    changed = []
    for dirpath, _, files in os.walk(ROOT):
        for name in files:
            if not name.endswith(".kt"):
                continue
            if name in SKIP_FILES:
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8") as f:
                content = f.read()
            if "Log." not in content and "android.util.Log" not in content:
                continue
            if migrate_file(path):
                changed.append(path)

    print(f"Migrated {len(changed)} files")
    for p in sorted(changed):
        print(f"  {p}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
