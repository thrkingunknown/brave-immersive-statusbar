/*
 * Copyright 2025 Morphe.
 * https://github.com/MorpheApp/morphe-patches-template
 */
package util

import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File
import java.net.URLClassLoader
import java.util.jar.Manifest

fun main() {
    val patchFiles = File("build/libs/").listFiles { file ->
        val fileName = file.name
        !fileName.contains("javadoc") &&
            !fileName.contains("sources") &&
            fileName.endsWith(".mpp")
    }?.sortedBy { it.name }
        ?: throw IllegalStateException("No patches build/libs directory found")

    require(patchFiles.isNotEmpty()) {
        "No .mpp patch bundle found under patches/build/libs; build the bundle first"
    }
    require(patchFiles.size == 1) {
        "Expected exactly one .mpp bundle after a clean build, found ${patchFiles.size}: ${patchFiles.joinToString { it.name }}"
    }

    val patchFile = patchFiles.single()
    val loadedPatches = loadPatchesFromJar(setOf(patchFile))
    val patchClassLoader = URLClassLoader(arrayOf(patchFile.toURI().toURL()))
    try {
        val manifests = patchClassLoader.getResources("META-INF/MANIFEST.MF")
        var foundVersion = false
        while (manifests.hasMoreElements()) {
            Manifest(manifests.nextElement().openStream()).mainAttributes
                .getValue("Version")
                ?.let { version ->
                    foundVersion = true
                    generatePatchList(version, loadedPatches)
                }
        }
        check(foundVersion) { "Bundle manifest has no Version attribute: ${patchFile.name}" }
    } finally {
        patchClassLoader.close()
    }
}

@Suppress("DEPRECATION")
private fun generatePatchList(version: String, patches: Set<Patch<*>>) {
    val listJson = File("../patches-list.json")
    val patchesMap = patches.sortedBy { it.name }.map { patch ->
        JsonPatch(
            name = patch.name!!,
            description = patch.description,
            default = patch.default,
            category = patch.category,
            dependencies = patch.dependencies.map { it.javaClass.simpleName },
            compatiblePackages = patch.compatibility?.map { compat ->
                JsonCompatibility(
                    packageName = compat.packageName!!,
                    name = compat.name,
                    description = compat.description,
                    apkFileType = compat.apkFileType?.name,
                    appIconColor = compat.appIconColor?.let { "#%06X".format(it) },
                    signatures = compat.signatures,
                    targets = compat.targets.map { target ->
                        JsonCompatibility.Target(
                            version = target.version,
                            versionCodes = target.versionCodes?.mapKeys { it.key.name },
                            isExperimental = target.isExperimental,
                            minSdk = target.minSdk,
                            description = target.description,
                        )
                    },
                )
            },
            options = patch.options.values.map { option ->
                JsonPatch.Option(
                    key = option.key,
                    title = option.title,
                    description = option.description,
                    required = option.required,
                    type = option.type.toString(),
                    default = option.default,
                    values = option.values,
                )
            },
        )
    }

    val gson = GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
    val jsonObject = JsonObject().apply {
        addProperty(
            "NOTE",
            "Do NOT manually edit this file. This file is automatically updated by the patch-list generator. " +
                "Manually editing this file can break source metadata and third-party tools.",
        )
        addProperty("version", version)
        add("patches", gson.toJsonTree(patchesMap))
    }
    listJson.writeText(gson.toJson(jsonObject))
    println("Generated ${patchesMap.size} patch entries in ${listJson.path}")
}

private class JsonPatch(
    val name: String? = null,
    val description: String? = null,
    val default: Boolean = true,
    val category: String? = null,
    val dependencies: List<String>,
    val compatiblePackages: List<JsonCompatibility>?,
    val options: List<Option>,
) {
    class Option(
        val key: String,
        val title: String?,
        val description: String?,
        val required: Boolean,
        val type: String,
        val default: Any?,
        val values: Map<String, Any?>?,
    )
}

private class JsonCompatibility(
    val packageName: String,
    val name: String?,
    val description: String?,
    val apkFileType: String?,
    val appIconColor: String?,
    val signatures: Set<String>?,
    val targets: List<Target>,
) {
    class Target(
        val version: String?,
        val versionCodes: Map<String, Int>?,
        val isExperimental: Boolean,
        val minSdk: Int?,
        val description: String?,
    )
}
