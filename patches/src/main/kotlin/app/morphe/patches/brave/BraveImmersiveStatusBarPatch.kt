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

private fun immersiveStatusBarPatch(
    patchName: String,
    compatibility: Compatibility,
) = bytecodePatch(
    name = patchName,
    description = "Hides the Android status bar when Brave's address bar scrolls out of view, and restores it when the bar returns.",
    default = true,
) {
    compatibleWith(compatibility)
    extendWith("extensions/extension.mpe")

    execute {
        // ChromeTabbedActivity.onResume() is a stable Chromium lifecycle entry point.
        // Install after the activity is resumed; the extension posts installation to the decor view,
        // so the toolbar hierarchy is available before it begins observation.
        val activityOnResumeFingerprint = Fingerprint(
            definingClass = "Lorg/chromium/chrome/browser/ChromeTabbedActivity;",
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

@Suppress("unused")
val braveImmersiveStatusBarPatch = immersiveStatusBarPatch(
    patchName = "Brave: Immersive status bar on hidden address bar",
    compatibility = BRAVE_COMPATIBILITY,
)

@Suppress("unused")
val braveBetaImmersiveStatusBarPatch = immersiveStatusBarPatch(
    patchName = "Brave Beta: Immersive status bar on hidden address bar",
    compatibility = BRAVE_BETA_COMPATIBILITY,
)
