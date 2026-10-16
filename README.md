# Brave Immersive Status Bar — community patch for Morphe

This project contains a Morphe Patcher source project for Android Brave and Brave Beta. It aims to hide the **status bar only** when Chromium's address/omnibox view is scrolled almost entirely off-screen, then restore it when the address bar returns.

## Compatibility metadata

- Brave: `com.brave.browser`
- Brave Beta: `com.brave.browser_beta`
- Version metadata is intentionally unpinned (`targets = emptyList()`), so the patch is offered to any version of those packages. This does **not** guarantee that every future APK's bytecode fingerprint will match; Morphe still needs to find `org.chromium.chrome.browser.ChromeTabbedActivity.onResume()V` when applying the patch.

## Behavior and implementation

- Hooks `ChromeTabbedActivity.onResume()` and defers observer installation until the UI queue advances.
- Prefers the `location_bar` resource; falls back to Chromium toolbar resource names if a layout variant differs.
- Measures the view's *visible screen area*, rather than only checking `View.VISIBLE`, because Chromium commonly hides controls with translations.
- Uses hide/show hysteresis (12% / 30% visible) to reduce flicker during toolbar animations.
- On Android 11+ hides/shows `WindowInsets.Type.statusBars()` and permits transient system-bar reveal by swipe. On older Android releases it uses fullscreen/immersive-sticky status-bar flags without hiding the navigation bar.
- Uses no polling timer. Once the address-bar view is found, it checks visibility during normal view pre-draws; lookup before that is event-driven by global layout.
- Runtime failures fail open instead of intentionally crashing the browser.

## Build

This is **source**, not a precompiled `.mpp` bundle. The `.mpp` artifact must be built with Morphe's Gradle plugin and its dependencies.

1. Install JDK 21+, Gradle 9.8.1, and an Android SDK (API 35 or newer).
2. Configure GitHub Packages credentials with a PAT that has `read:packages` access. Add to `~/.gradle/gradle.properties`:

   ```properties
   gpr.user = YOUR_GITHUB_USERNAME
   gpr.key = YOUR_GITHUB_PAT
   ```

3. From this directory, run:

   ```bash
   gradle buildAndroid
   ```

4. The official template build convention writes the patch bundle under `patches/build/libs/` with the `.mpp` extension. Import that `.mpp` in Morphe Manager/Desktop and patch a Brave APK of the matching package.

## GitHub Actions build

The `.github/workflows/build.yml` workflow follows the Morphe template build convention and:

- builds automatically on every push/commit to any branch;
- can also be started manually from **GitHub → Actions → Build Morphe Patch → Run workflow**;
- uses Temurin Java 21 and Gradle 9.8.1, matching the current Morphe patches template toolchain;
- runs `gradle :patches:buildAndroid --no-daemon --stacktrace`;
- removes stale build output before compiling;
- runs the Morphe-template `generatePatchesList` task to create `patches-list.json` from the compiled `.mpp`;
- fails unless the bundle exposes exactly the two expected named patches (Brave Stable and Brave Beta), rather than merely checking that a file exists;
- uploads the `.mpp`, `patches-list.json`, and `SHA256SUMS.txt` as a workflow artifact retained for 14 days.

Download the artifact from the completed workflow run's **Artifacts** section. This is an Actions artifact, not a GitHub Release or a public Morphe source release.

### Package-registry authentication

The workflow first uses the run's automatic `GITHUB_TOKEN`. If dependency resolution fails with a GitHub Packages `401`/`403`, add a repository Actions secret named `MORPHE_GITHUB_TOKEN` containing a GitHub personal access token with `read:packages` access. The workflow automatically prefers that secret when present. Never put a token directly in the workflow or commit it to the repository.

## Fix for a source showing “Patches 0”

The earlier source omitted Morphe's template-style `generatePatchesList` task, so it could produce an `.mpp` without validating that the bundle's named patches were discoverable. The build now runs that generator and asserts that both named Brave patches are present. If that assertion fails in GitHub Actions, the `.mpp` is not uploaded.

After the workflow succeeds, download the artifact and re-import the freshly built `.mpp`; removing/replacing the older local source is necessary because updating the project ZIP cannot change an already imported bundle.

## Validation status

The patch list generator and CI guard are included and statically checked. This environment does not have the Morphe registry dependencies or a Brave APK available for a true Gradle build/application test. Therefore no claim is made that a newly built `.mpp` has passed runtime testing. The GitHub Actions job is designed to perform the first authoritative bundle-discovery check and fail closed if it detects zero or partial patches.

## License and naming

GPL-3.0-or-later. This is an independent community patch and is not authored, endorsed, or distributed by the Morphe project. “Morphe” is used only to describe compatibility.
