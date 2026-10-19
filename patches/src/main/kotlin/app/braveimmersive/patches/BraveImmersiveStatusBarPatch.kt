package app.braveimmersive.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.braveimmersive.patches.shared.Constants.BRAVE_COMPATIBILITY
import app.braveimmersive.patches.shared.Constants.BRAVE_BETA_COMPATIBILITY

private const val EXTENSION = "Lapp/braveimmersive/extension/BraveImmersiveStatusBar;"

private val currentActivityResumeFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;",
    name = "onResume",
    returnType = "V",
    parameters = emptyList()
)

// Patch the concrete compositor observer because it overrides these callbacks rather
// than inheriting the interface default implementations.
private val controlsOffsetChangedFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;",
    name = "onControlsOffsetChanged",
    returnType = "V",
    parameters = listOf("I", "I", "Z", "I", "I", "Z", "Z", "Z")
)

private val topControlsHeightChangedFingerprint = Fingerprint(
    definingClass = "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;",
    name = "onTopControlsHeightChanged",
    returnType = "V",
    parameters = listOf("I", "I")
)

@JvmField
@Suppress("unused")
val braveImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Immersive Status Bar",
    description = "Hides the Android status bar when Brave's top browser controls are fully collapsed, and restores it as they reappear.",
    default = true
) {
    compatibleWith(BRAVE_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        currentActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION->setCurrentActivity(Landroid/app/Activity;)V
            """
        )

        controlsOffsetChangedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onControlsOffsetChanged(II)V
            """
        )

        topControlsHeightChangedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onTopControlsHeightChanged(II)V
            """
        )
    }
}

@JvmField
@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Beta Immersive Status Bar",
    description = "Hides the Android status bar when Brave Beta's top browser controls are fully collapsed, and restores it as they reappear.",
    default = true
) {
    compatibleWith(BRAVE_BETA_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        currentActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION->setCurrentActivity(Landroid/app/Activity;)V
            """
        )

        controlsOffsetChangedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onControlsOffsetChanged(II)V
            """
        )

        topControlsHeightChangedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onTopControlsHeightChanged(II)V
            """
        )
    }
}
