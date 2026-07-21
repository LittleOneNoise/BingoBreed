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

        // Logs écrits dans <racine du repo>/logs/ (cf. logback.xml) plutôt que dans le profil
        // utilisateur : plus simple à retrouver/suivre pendant une session de debug en jeu.
        jvmArgs += "-Dbingobreed.projectDir=${rootProject.projectDir}"

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