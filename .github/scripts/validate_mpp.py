#!/usr/bin/env python3
"""Validate and stage a loadable Android Morphe .mpp artifact.

Morphe Manager loads patch implementations from root classes.dex entries. The
runtime bridge is a separate DEX payload at extensions/extension.mpe. A JVM
bundle containing Kotlin .class files plus extension.mpe is not installable.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[2]
EXPECTED_PATCHES = {
    "Brave Immersive Status Bar",
    "Brave Beta Immersive Status Bar",
}
EXPECTED_PATCH_SYMBOLS = (
    b"BraveImmersiveStatusBarPatchKt",
    b"BraveBetaImmersiveStatusBarPatchKt",
    b"braveImmersiveStatusBarPatch",
    b"braveBetaImmersiveStatusBarPatch",
)
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


def valid_dex(data: bytes) -> bool:
    # DEX headers are 112 bytes minimum and use the "dex\nNNN\0" magic.
    return len(data) >= 112 and re.match(rb"^dex\n[0-9]{3}\x00", data) is not None


def main() -> int:
    inventory_path = ROOT / "patches-list.json"
    if not inventory_path.is_file():
        fail(f"Missing generated patch inventory: {inventory_path}")
    try:
        inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"Generated patches-list.json is not valid JSON: {exc}")

    version = str(inventory.get("version", "")).strip()
    if not version or version.lower() == "unspecified":
        fail(f"Invalid inventory version: {version!r}; pass -Pversion explicitly to Gradle")
    actual_names = {
        patch.get("name")
        for patch in inventory.get("patches", [])
        if isinstance(patch, dict)
    }
    missing = EXPECTED_PATCHES - actual_names
    if missing:
        fail(f"Inventory is missing patches {sorted(missing)}; found {sorted(actual_names)}")

    libs = ROOT / "patches" / "build" / "libs"
    candidates = sorted(
        path for path in libs.glob("*.mpp")
        if not path.name.lower().endswith(("-sources.mpp", "-javadoc.mpp"))
    )
    if len(candidates) != 1:
        fail(f"Expected exactly one installable MPP in {libs}, found {len(candidates)}: {[p.name for p in candidates]}")

    bundle = candidates[0]
    if bundle.name != f"patches-{version}.mpp":
        fail(f"Inventory version {version!r} does not match bundle filename {bundle.name!r}")

    try:
        with zipfile.ZipFile(bundle) as archive:
            names = set(archive.namelist())
            if "META-INF/MANIFEST.MF" not in names:
                fail("Bundle has no META-INF/MANIFEST.MF; it is not a Morphe patch bundle")
            manifest = parse_manifest(
                archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
            )
            if manifest.get("Name") != "Brave Immersive Patches":
                fail(f"Unexpected bundle Name: {manifest.get('Name')!r}")
            if manifest.get("Version") != version:
                fail(f"Manifest version {manifest.get('Version')!r} differs from inventory {version!r}")
            if not manifest.get("Patcher-Version"):
                fail("Bundle manifest has no Patcher-Version")

            dex_names = sorted(
                name for name in names
                if "/" not in name and re.fullmatch(r"classes(?:[0-9]+)?\.dex", PurePosixPath(name).name)
            )
            if not dex_names:
                class_files = sorted(name for name in names if name.endswith(".class"))
                fail(
                    "Patch bundle is missing root DEX entries (classes.dex/classes2.dex...). "
                    "This is the exact failure Morphe Manager reports as 'Patch bundle is missing dex entries'. "
                    f"Found {len(class_files)} JVM .class files; rebuild Android MPP last with :patches:buildAndroid."
                )

            dex_payloads = []
            for dex_name in dex_names:
                payload = archive.read(dex_name)
                if not valid_dex(payload):
                    fail(f"{dex_name} is not a valid DEX payload")
                dex_payloads.append(payload)
            combined_dex = b"\n".join(dex_payloads)
            for symbol in EXPECTED_PATCH_SYMBOLS:
                if symbol not in combined_dex:
                    fail(f"Patch DEX is missing expected class/symbol {symbol.decode()!r}")

            extension_name = "extensions/extension.mpe"
            if extension_name not in names:
                fail(f"Bundle is missing runtime extension {extension_name}")
            extension_dex = archive.read(extension_name)
            if not valid_dex(extension_dex):
                fail("Runtime extension extensions/extension.mpe is not a valid DEX file")
            for required in EXPECTED_EXTENSION_STRINGS:
                if required not in extension_dex:
                    fail(f"Runtime DEX is missing class/method string {required.decode()!r}")
    except zipfile.BadZipFile as exc:
        fail(f"Installable MPP is not a valid ZIP/JAR archive: {exc}")

    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    for previous in dist.iterdir():
        if previous.is_file():
            previous.unlink()
    staged_bundle = dist / bundle.name
    staged_bundle.write_bytes(bundle.read_bytes())
    digest = hashlib.sha256(staged_bundle.read_bytes()).hexdigest()
    (dist / "SHA256SUMS.txt").write_text(
        f"{digest}  {staged_bundle.name}\n", encoding="utf-8"
    )

    print(
        f"Validated {len(EXPECTED_PATCHES)} patch entries, version {version}, "
        f"Patcher-Version {manifest['Patcher-Version']}, patch DEX {', '.join(dex_names)}, "
        f"runtime extension ({len(extension_dex):,} bytes); staged {staged_bundle.name}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
