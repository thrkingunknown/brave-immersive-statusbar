group = "app.community.braveimmersive"

patches {
    about {
        name = "Brave Immersive Status Bar"
        description = "Automatically hides Brave's Android status bar while the address bar is scrolled out of view."
        source = "https://github.com/thrkingunknown/brave-immersive-statusbar"
        author = "Community patch contributors"
        contact = "https://github.com/thrkingunknown/brave-immersive-statusbar/issues"
        website = "https://github.com/thrkingunknown/brave-immersive-statusbar"
        license = "GPLv3"
    }
}

// Keep Gson available to the standard Morphe patch-list generator at runtime,
// without including Gson inside the patch bundle itself.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Generate patches-list.json from the compiled Morphe patch bundle"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Keep the generated source metadata in step with the bundle during publishing.
    publish {
        dependsOn("generatePatchesList")
    }
}
