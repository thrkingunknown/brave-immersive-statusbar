/*
 * Copyright (C) 2026 Community patch contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package app.morphe.patches.brave

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch

private val BRAVE_COMPATIBILITY = Compatibility(
    name = "Brave",
    packageName = "com.brave.browser",
    appIconColor = 0xFB542B,
)

private val BRAVE_BETA_COMPATIBILITY = Compatibility(
    name = "Brave Beta",
    packageName = "com.brave.browser_beta",
    appIconColor = 0xFB542B,
)

private const val CHROME_TABBED_ACTIVITY = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;"

/**
 * Keep these patch values as explicit public JVM fields. Morphe's bundle loader discovers
 * statically exposed patch objects from the compiled .mpp; Kotlin's default top-level `val`
 * backing fields are private. @JvmField makes the patch objects unambiguously discoverable.
 */
@JvmField
@Suppress("unused")
val braveImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave: Immersive status bar on hidden address bar",
    description = "Hides the Android status bar when Brave's address bar is hidden and restores it when the bar reappears.",
    default = true,
) {
    compatibleWith(BRAVE_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        val activityOnResumeFingerprint = Fingerprint(
            definingClass = CHROME_TABBED_ACTIVITY,
            name = "onResume",
            returnType = "V",
            parameters = emptyList(),
        )

        activityOnResumeFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/BraveImmersiveStatusBar;->install(Landroid/app/Activity;)V",
        )
    }
}

@JvmField
@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = bytecodePatch(
    name = "Brave Beta: Immersive status bar on hidden address bar",
    description = "Hides the Android status bar when Brave Beta's address bar is hidden and restores it when the bar reappears.",
    default = true,
) {
    compatibleWith(BRAVE_BETA_COMPATIBILITY)
    extendWith("extensions/extension.mpe")

    execute {
        val activityOnResumeFingerprint = Fingerprint(
            definingClass = CHROME_TABBED_ACTIVITY,
            name = "onResume",
            returnType = "V",
            parameters = emptyList(),
        )

        activityOnResumeFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, Lapp/morphe/extension/BraveImmersiveStatusBar;->install(Landroid/app/Activity;)V",
        )
    }
}
