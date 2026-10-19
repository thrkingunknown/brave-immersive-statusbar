package app.braveimmersive.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION = "Lapp/braveimmersive/extension/BraveImmersiveStatusBar;"

private fun braveBetaCompatibility() = Compatibility(
    name = "Brave Beta",
    packageName = "com.brave.browser_beta",
    apkFileType = ApkFileType.APK,
    targets = listOf(AppTarget(version = null, isExperimental = true)),
)

@JvmField
@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Beta Immersive Status Bar",
    description = "Hides the Android status bar when Brave Beta's top browser controls are fully collapsed, and restores it as they reappear.",
    default = true,
) {
    compatibleWith(braveBetaCompatibility())
    extendWith("extensions/extension.mpe")

    // Same Chrome/Chromium callbacks as Brave stable, kept separate so a class-init failure in one
    // definition cannot silently remove both patches from the bundle.
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
