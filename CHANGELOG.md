
## Unreleased

- Fix Brave Beta 1.99.46 fingerprint failure by removing the obsolete exact eight-parameter descriptor for `CompositorViewHolder.onControlsOffsetChanged`. The fingerprint now uses the stable owner, method name, and return type. Runtime application must still be verified against the target APK.

