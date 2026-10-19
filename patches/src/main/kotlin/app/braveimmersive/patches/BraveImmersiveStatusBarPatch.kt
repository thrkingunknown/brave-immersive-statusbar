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

private val controlsOffsetChangedFingerprint = Fingerprint(
    definingClass = ":org/chromium/chrome/browser/",
    name = "onControlsOffsetChanged",
    returnType = "V",
    parameters = listOf("I", "I", "Z", "I", "I", "Z", "Z", "Z")
)

@JvmField
@Suppress("unused")
val braveImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Immersive Status Bar",
    description = "Hides Android's status bar when Brave's top browser controls are fully collapsed, and restores it when they reappear.",
    default = true
) {
    compatibleWith(BRAVE_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        // Keep a reference to the currently resumed tabbed-browser Activity.
        currentActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION->setCurrentActivity(Landroid/app/Activity;)V
            """
        )

        // Chromium's current BrowserControlsStateProvider.Observer callback has
        // eight parameters. topOffset is p1; topControlsMinHeightOffset is p2.
        // The status bar changes only at fully shown/fully collapsed thresholds.
        controlsOffsetChangedFingerprint.method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onControlsOffsetChanged(II)V
            """
        )
    }
}


@JvmField
@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Beta Immersive Status Bar",
    description = "Hides Android's status bar when Brave Beta's top browser controls are fully collapsed, and restores it when they reappear.",
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
    }
}
