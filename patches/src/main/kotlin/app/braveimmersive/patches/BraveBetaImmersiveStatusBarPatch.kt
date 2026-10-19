package app.braveimmersive.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.braveimmersive.patches.shared.Constants.BRAVE_BETA_COMPATIBILITY

private const val EXTENSION = "Lapp/braveimmersive/extension/BraveImmersiveStatusBar;"
private const val COMPOSITOR_VIEW_HOLDER =
    "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;"

@JvmField
@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Beta Immersive Status Bar",
    description = "Hides the Android status bar when Brave Beta's top browser controls are fully collapsed, and restores it as they reappear.",
    default = true,
) {
    compatibleWith(BRAVE_BETA_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;",
            name = "onResume",
            returnType = "V",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION->setCurrentActivity(Landroid/app/Activity;)V
            """.trimIndent(),
        )

        // Prefer the source-level method name when it is present. Brave Beta 1.99.46
        // obfuscates this callback to E(IIIIZZZZ)V, so fall back to the exact signature
        // confirmed in that APK rather than matching an arbitrary method by owner alone.
        val controlsOffsetChangedMethod = runCatching {
            Fingerprint(
                definingClass = COMPOSITOR_VIEW_HOLDER,
                name = "onControlsOffsetChanged",
                returnType = "V",
            ).method
        }.getOrElse {
            Fingerprint(
                definingClass = COMPOSITOR_VIEW_HOLDER,
                name = "E",
                returnType = "V",
                parameters = listOf("I", "I", "I", "I", "Z", "Z", "Z", "Z"),
            ).method
        }

        controlsOffsetChangedMethod.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onControlsOffsetChanged(II)V
            """.trimIndent(),
        )

        // The source-level onTopControlsHeightChanged name is absent from this minified class.
        // It has multiple obfuscated (II)V methods, and their roles have not been established
        // from the available disassembly. Do not inject into an unverified method; the extension
        // uses its density-based fallback threshold when the height callback is unavailable.
    }
}
