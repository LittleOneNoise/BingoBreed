import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "fr.lilone.bingobreed.MainKt"

        // Mode diagnostic : `./gradlew :desktopApp:run -Pdiagnostic`
        if (project.hasProperty("diagnostic")) {
            jvmArgs += "-Dbingobreed.diagnostic=true"
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "fr.lilone.bingobreed"
            packageVersion = version.toString()
        }
    }
}