# Validation notes

## Fix applied for the “Patches 0” report

- Exported both user-facing patch objects with `@JvmField`, making the patch instances explicit public static JVM fields instead of Kotlin's default private backing fields. This addresses bundle scanners that enumerate JVM fields.
- Added the Morphe-template `generatePatchesList` Gradle task and its dedicated Gson classpath.
- Added `util.PatchListGeneratorKt`, which loads the generated `.mpp` using Morphe Patcher's `loadPatchesFromJar` API and serializes the actually discoverable patch metadata to root `patches-list.json`. It now fails immediately if the loader finds zero patches.
- Updated GitHub Actions to clean old build products before the build, generate the patch list, and require exactly these two names before uploading anything:
  - `Brave: Immersive status bar on hidden address bar`
  - `Brave Beta: Immersive status bar on hidden address bar`
- CI checks for exactly one `.mpp` artifact and uploads the artifact, metadata list, and SHA-256 checksum.

## Checks performed in this environment

- Verified source paths align with Gradle's Kotlin source-set layout.
- Verified both patch declarations are named, use the intended package names and Morphe patch DSL, and are annotated with `@JvmField`.
- Verified the workflow triggers on every branch push and `workflow_dispatch`.
- Parsed workflow YAML and checked the patch-list guard logic.
- Inspected the current Morphe template and Patcher documentation: the standard template generates `patches-list.json` from the compiled bundle, and `PatchLoader` discovers named patches from public static properties/getters.

## Not performed

- A real Gradle build: Morphe GitHub Packages dependencies require registry access credentials which are not available in this environment.
- Applying either patch to an actual Brave Stable or Brave Beta APK.
- Runtime verification on Android hardware.

The CI job now fails if the built bundle does not expose both patch entries. Once CI passes, download the fresh `.mpp` artifact and replace the previously imported local source in Morphe; an already imported `.mpp` remains unchanged until replaced.

## Additional source-level check for the reported zero-patch bundle

- Compiled the patch declaration file with Kotlin 1.9 against minimal API-shape stubs and inspected the emitted JVM class using `javap`.
- Confirmed both named patches are emitted as `public static final` fields, so they are discoverable by bundle loaders that enumerate JVM patch fields.
- This is not a substitute for the Morphe Gradle build or an on-device import test. The source ZIP must be rebuilt into a fresh primary `.mpp`; an already imported local source remains the old bundle until replaced.
