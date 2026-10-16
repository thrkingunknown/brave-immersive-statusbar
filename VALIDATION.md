# Validation notes

## Fix applied for the “Patches 0” report

- Added the Morphe-template `generatePatchesList` Gradle task and its dedicated Gson classpath.
- Added `util.PatchListGeneratorKt`, which loads the generated `.mpp` using Morphe Patcher's `loadPatchesFromJar` API and serializes the actually discoverable patch metadata to root `patches-list.json`.
- Updated GitHub Actions to clean old build products before the build, generate the patch list, and require exactly these two names before uploading anything:
  - `Brave: Immersive status bar on hidden address bar`
  - `Brave Beta: Immersive status bar on hidden address bar`
- CI checks for exactly one `.mpp` artifact and uploads the artifact, metadata list, and SHA-256 checksum.

## Checks performed in this environment

- Verified source paths align with Gradle's Kotlin source-set layout.
- Verified both patch declarations are named, use the intended package names, and use the Morphe patch DSL.
- Verified the workflow triggers on every branch push and `workflow_dispatch`.
- Parsed workflow YAML and checked the patch-list guard logic.
- Inspected the current Morphe template and Patcher documentation: the standard template generates `patches-list.json` from the compiled bundle, and `PatchLoader` discovers named patches from public static properties/getters.

## Not performed

- A real Gradle build: Morphe GitHub Packages dependencies require registry access credentials which are not available in this environment.
- Applying either patch to an actual Brave Stable or Brave Beta APK.
- Runtime verification on Android hardware.

The CI job now fails if the built bundle does not expose both patch entries. Once CI passes, download the fresh `.mpp` artifact and replace the previously imported local source in Morphe; an already imported `.mpp` remains unchanged until replaced.


## Manager discovery fix (2026-10-16)

Both top-level patch declarations are annotated with `@JvmField`. This emits public static JVM fields for the Morphe patch loader instead of relying on Kotlin property getters/private backing fields. This specifically targets the observed Manager UI showing `Patches 0`. A real Gradle build and import into Morphe Manager are still required to confirm discovery end-to-end.
