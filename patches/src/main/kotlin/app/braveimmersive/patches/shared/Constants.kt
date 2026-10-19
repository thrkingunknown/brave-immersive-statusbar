package app.braveimmersive.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Compatibility declarations shared by the two patch entries.
 *
 * appIconColor must be a 24-bit 0xRRGGBB value; do not include an alpha byte.
 */
object Constants {
    val BRAVE_COMPATIBILITY = Compatibility(
        name = "Brave",
        packageName = "com.brave.browser",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x5E35B1,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    )

    val BRAVE_BETA_COMPATIBILITY = Compatibility(
        name = "Brave Beta",
        packageName = "com.brave.browser_beta",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x5E35B1,
        targets = listOf(AppTarget(version = null, isExperimental = true)),
    )
}
