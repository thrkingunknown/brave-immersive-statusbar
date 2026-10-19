package app.braveimmersive.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION = "Lapp/braveimmersive/extension/BraveImmersiveStatusBar;"

private fun braveCompatibility() = Compatibility(
    name = "Brave",
    packageName = "com.brave.browser",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

@JvmField
@Suppress("unused")
val braveImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Immersive Status Bar",
    description = "Hides the Android status bar when Brave's top browser controls are fully collapsed, and restores it as they reappear.",
    default = true,
) {
    compatibleWith(braveCompatibility())
    extendWith("extensions/extension.mpe")

    // Keep Chromium fingerprints out of the patch class's static initializer. This ensures that
    // merely enumerating the source does not have to resolve app-specific fingerprint details.
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

        Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;",
            name = "onControlsOffsetChanged",
            returnType = "V",
            parameters = listOf("I", "I", "Z", "I", "I", "Z", "Z", "Z"),
        ).method.addInstructions(
            0,
            // In Chromium's 8-argument observer, p1 is topOffset and p2 is
            // topControlsMinHeightOffset; the receiver occupies p0.
            """
                invoke-static {p1, p2}, $EXTENSION->onControlsOffsetChanged(II)V
            """.trimIndent(),
        )

        Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/compositor/CompositorViewHolder;",
            name = "onTopControlsHeightChanged",
            returnType = "V",
            parameters = listOf("I", "I"),
        ).method.addInstructions(
            0,
            """
                invoke-static {p1, p2}, $EXTENSION->onTopControlsHeightChanged(II)V
            """.trimIndent(),
        )
    }
}
