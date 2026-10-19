group = "app.braveimmersive"
version = providers.gradleProperty("version").getOrElse("0.1.0")

patches {
    // TODO: Update this section with your project details.
    about {
        name = "Brave Immersive Patches"
        description = "Experimental immersive status-bar patch for Brave on Android"
        source = "https://github.com/thrkingunknown/brave-immersive-statusbar"
        author = "thrkingunknown"
        contact = "https://github.com/thrkingunknown/brave-immersive-statusbar/issues"
        website = "https://github.com/thrkingunknown/brave-immersive-statusbar"
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
