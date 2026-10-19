#!/usr/bin/env python3
"""Fail CI unless the exact installable Morphe bundle contains both patches and runtime DEX."""
from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_PATCHES = {
    "Brave Immersive Status Bar",
    "Brave Beta Immersive Status Bar",
}
EXPECTED_CLASS_ENTRIES = {
    "app/braveimmersive/patches/BraveImmersiveStatusBarPatchKt.class",
    "app/braveimmersive/patches/BraveBetaImmersiveStatusBarPatchKt.class",
}
EXPECTED_EXTENSION_STRINGS = (
    b"BraveImmersiveStatusBar",
    b"setCurrentActivity",
    b"onControlsOffsetChanged",
    b"onTopControlsHeightChanged",
)


def fail(message: str) -> "NoReturn":
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(message)


def parse_manifest(text: str) -> dict[str, str]:
    attrs: dict[str, str] = {}
    current: str | None = None
    for line in text.replace("\r\n", "\n").split("\n"):
        if line.startswith(" ") and current:
            attrs[current] += line[1:]
            continue
        if not line:
            current = None
            continue
        if ": " not in line:
            continue
        current, value = line.split(": ", 1)
        attrs[current] = value
    return attrs


def main() -> int:
    inventory_path = ROOT / "patches-list.json"
    if not inventory_path.is_file():
        fail(f"Missing generated inventory: {inventory_path}")
    try:
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"Generated patches-list.json is not valid JSON: {exc}")

    version = str(inventory.get("version", "")).strip()
    if not version or version.lower() == "unspecified":
        fail(f"Invalid inventory version: {version!r}")
    actual_names = {p.get("name") for p in inventory.get("patches", []) if isinstance(p, dict)}
    missing = EXPECTED_PATCHES - actual_names
    if missing:
        fail(f"Inventory missing patches {sorted(missing)}; found {sorted(actual_names)}")

    libs = ROOT / "patches" / "build" / "libs"
    candidates = sorted(
        path for path in libs.glob("*.mpp")
        if not path.name.endswith(("-sources.mpp", "-javadoc.mpp"))
    )
    if len(candidates) != 1:
        fail(f"Expected exactly one installable MPP, found {len(candidates)}: {[p.name for p in candidates]}")
    bundle = candidates[0]
    expected_filename = f"patches-{version}.mpp"
    if bundle.name != expected_filename:
        fail(f"Inventory version {version!r} does not match bundle filename {bundle.name!r}")

    try:
        with zipfile.ZipFile(bundle) as archive:
            names = set(archive.namelist())
            if "META-INF/MANIFEST.MF" not in names:
                fail("Bundle has no META-INF/MANIFEST.MF; it is not an installable Morphe bundle")
            manifest = parse_manifest(archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace"))
            if manifest.get("Name") != "Brave Immersive Patches":
                fail(f"Unexpected bundle Name in manifest: {manifest.get('Name')!r}")
            if manifest.get("Version") != version:
                fail(f"Manifest version {manifest.get('Version')!r} differs from inventory {version!r}")
            if not manifest.get("Patcher-Version"):
                fail("Bundle manifest has no Patcher-Version")

            missing_classes = EXPECTED_CLASS_ENTRIES - names
            if missing_classes:
                fail(f"Bundle is missing patch class files: {sorted(missing_classes)}")
            for class_entry, patch_name in zip(sorted(EXPECTED_CLASS_ENTRIES), sorted(EXPECTED_PATCHES)):
                payload = archive.read(class_entry)
                if patch_name.encode("utf-8") not in payload:
                    fail(f"Patch name {patch_name!r} was not found in bytecode entry {class_entry}")

            extension_name = "extensions/extension.mpe"
            if extension_name not in names:
                fail(f"Bundle is missing runtime extension {extension_name}")
            dex = archive.read(extension_name)
            if len(dex) < 4096 or not dex.startswith(b"dex\n"):
                fail("Runtime extension is missing or is not a valid DEX file")
            for required in EXPECTED_EXTENSION_STRINGS:
                if required not in dex:
                    fail(f"Runtime DEX is missing required class/method string {required.decode()!r}")
    except zipfile.BadZipFile as exc:
        fail(f"Primary MPP is not a valid ZIP/JAR archive: {exc}")

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    for previous in dist.iterdir():
        if previous.is_file():
            previous.unlink()
    staged_bundle = dist / bundle.name
    staged_bundle.write_bytes(bundle.read_bytes())
    digest = hashlib.sha256(staged_bundle.read_bytes()).hexdigest()
    (dist / "SHA256SUMS.txt").write_text(f"{digest}  {staged_bundle.name}\n", encoding="utf-8")

    print(
        f"Validated {len(EXPECTED_PATCHES)} named patches, version {version}, "
        f"Patcher-Version {manifest['Patcher-Version']}, runtime extension ({len(dex):,} bytes); "
        f"staged only {staged_bundle.name}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
