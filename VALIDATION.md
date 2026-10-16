# Validation report

Validation date: 2026-10-16

## Completed

- ZIP contents and archive integrity checked.
- Java extension source compiled with `javac` against minimal Android API-shape stubs to catch Java syntax and type-use errors.
- Kotlin patch source compiled with `kotlinc` against minimal Morphe API-shape stubs to catch Kotlin syntax and expected API-shape usage.
- Static checks confirmed both Brave package IDs, the `ChromeTabbedActivity.onResume()V` hook, and extension bundle reference.

## Not completed

- No real Gradle build was possible: Gradle, Android SDK/`android.jar`, Morphe Gradle dependencies, and network access to the dependency registry were unavailable in the build container.
- No `.mpp` bundle was produced.
- No Brave/Brave Beta APK was patched and no on-device behavior test was performed.

The stub compilation is a source-level check only; it does not establish compatibility with the real Morphe plugin/dependency versions, DEX verifier, or Brave UI hierarchy.


## CI builder update (2026-10-16)

- Added `.github/workflows/build.yml` with `push` (all branches) and `workflow_dispatch` triggers.
- Uses the current Morphe template's Java 21 / Gradle 9.8.1 toolchain conventions and `:patches:buildAndroid` build task.
- Performs a post-build check for one or more `.mpp` outputs, writes SHA-256 checksums, and uploads the files as a 14-day GitHub Actions artifact.
- Static workflow/YAML structure and referenced file paths can be checked locally; this environment cannot run a GitHub-hosted workflow or authenticate to Morphe's package registry, so an actual remote CI build remains unverified until the workflow is run in the target repository.
- Local static checks completed successfully for YAML parsing, the `push`/`workflow_dispatch` triggers, action references, Gradle build command, artifact paths, and alignment with `settings.gradle.kts` registry credential environment variables.
