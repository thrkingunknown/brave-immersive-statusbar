## Unreleased

- Fix the Brave Beta 1.99.46 controls-offset fingerprint using the APK-verified obfuscated method `CompositorViewHolder.E(IIIIZZZZ)V`, with a source-name fallback for builds that retain `onControlsOffsetChanged`.
- Remove the unverified source-name-based Beta top-controls-height fingerprint. The supplied disassembly shows obfuscated `(II)V` methods but does not yet establish which one represents the top-controls-height callback; the extension therefore uses its density-based fallback threshold for this target.
- Runtime application and status-bar behavior remain unverified; this is a source-level correction based on the supplied APK's disassembly.
