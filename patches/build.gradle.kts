group = "app.braveimmersive"

patches {
    // TODO: Update this section with your project details.
    about {
        name = "Brave Immersive Patches"
        description = "Experimental immersive status-bar patch for Brave on Android"
        source = "https://github.com/"
        author = "Community patch"
        contact = "See repository issues"
        website = "https://github.com/"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

// Important: this generator reads the JVM .mpp so it can reflect over patch classes.
// Release automation must run :patches:buildAndroid AFTER generatePatchesList; otherwise
// the JVM-only .mpp can overwrite the Android bundle and Morphe Manager reports
// "Patch bundle is missing dex entries".
tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
