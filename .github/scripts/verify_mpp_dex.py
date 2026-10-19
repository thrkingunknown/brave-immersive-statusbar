#!/usr/bin/env python3
"""Validate that installable Morphe .mpp bundles contain Android DEX entries.

Morphe Manager loads patch implementations from classes.dex, classes2.dex, etc.
A JVM-only .mpp containing *.class files can compile successfully but is rejected
by Manager with: "Patch bundle is missing dex entries".
"""
from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path, PurePosixPath


def main() -> int:
    directory = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("patches/build/libs")
    bundles = sorted(
        path for path in directory.glob("*.mpp")
        if "sources" not in path.name.lower() and "javadoc" not in path.name.lower()
    )
    if not bundles:
        print(f"ERROR: No installable .mpp bundle found in {directory}", file=sys.stderr)
        return 1

    failed = False
    for bundle in bundles:
        try:
            with zipfile.ZipFile(bundle) as archive:
                dex_entries = [
                    name for name in archive.namelist()
                    if re.fullmatch(r"classes(?:[0-9]+)?\.dex", PurePosixPath(name).name)
                    and "/" not in name.rstrip("/")
                ]
                if not dex_entries:
                    print(
                        f"ERROR: {bundle}: no root classes.dex/classesN.dex entries found; "
                        "this is a JVM-only bundle that Morphe Manager cannot load.",
                        file=sys.stderr,
                    )
                    failed = True
                else:
                    print(f"OK: {bundle}: found {', '.join(dex_entries)}")
        except (OSError, zipfile.BadZipFile) as exc:
            print(f"ERROR: {bundle}: unreadable ZIP archive: {exc}", file=sys.stderr)
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
