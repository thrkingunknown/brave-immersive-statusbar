# Brave Immersive Status Bar — patches compatible with Morphe

Experimental patch source for Brave on Android. When Chromium reports the top browser controls fully collapsed, the patch hides the Android status bar; when the controls return to offset zero, it shows the status bar again.

**Status: experimental, source/build validation only.** This source has not been verified against real Brave Stable and Brave Beta APKs or on a device. Chromium observer signatures and activity fingerprints can change between releases. Do not treat this as confirmed support for every current/future version until those targets are tested.

## Build
- JDK 21 and access to `https://maven.pkg.github.com/MorpheApp/registry`.
- Set `GITHUB_ACTOR` and a GitHub token with `read:packages` access as `GITHUB_TOKEN`, or configure `gpr.user` and `gpr.key` in Gradle properties.
- Generate metadata first, then build the Android bundle last so the JVM-only `.mpp` cannot overwrite the installable DEX-bearing bundle:

  ```bash
  ./gradlew clean :patches:generatePatchesList --no-daemon -Pversion=0.1.0
  ./gradlew :patches:buildAndroid --no-daemon -Pversion=0.1.0
  python3 .github/scripts/validate_mpp.py
  ```

- Output: `dist/patches-0.1.0.mpp` after validation; the Gradle artifact is in `patches/build/libs/`.

Import the generated `.mpp` into Morphe as a local patch bundle. Do not import the source ZIP or a `-sources.mpp` / `-javadoc.mpp` file as a patch bundle.

## Behavior and limitations
- Targets Chromium's current `onControlsOffsetChanged(int, int, boolean, int, int, boolean, boolean, boolean)` observer signature.
- Tracks the resumed `ChromeTabbedActivity` and uses Android `WindowInsetsController` on API 30+, with immersive system-UI flags on older Android.
- Does not force edge-to-edge layout globally, avoiding unintended Brave content-inset changes.
- If either fingerprint fails on a particular Brave build, the patch cannot be applied to that APK.
- Transient system-bar reveal behavior may vary by Android version and OEM.

## Validation checklist
1. Build the `.mpp` with GitHub Actions.
2. Apply to untouched Brave Stable and Brave Beta APKs separately.
3. Confirm Morphe lists the patch and both fingerprints resolve.
4. Scroll until top controls fully collapse: status bar should hide without flicker.
5. Scroll upward until controls return to offset zero: status bar should reappear.
6. Test gestures, rotation, tab switching, background/foreground, fullscreen video, and Android 11–16.
7. Check logcat and verify the status bar is not left hidden after leaving the browser screen.

## License
GPL-3.0-or-later. This is an independent patch and is not affiliated with the Morphe project.

## Installing a CI build

Download the single `patches-<version>.mpp` file from the **brave-immersive-statusbar-installable-mpp** Actions artifact or the matching GitHub prerelease. Do not import a `-sources.mpp` or `-javadoc.mpp` archive. The workflow publishes only a bundle whose root patch DEX, extension bridge DEX, package/version metadata, and both patch symbols pass validation.

