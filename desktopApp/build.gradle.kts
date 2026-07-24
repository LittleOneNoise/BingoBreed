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
            // Nom du lanceur (bingobreed.exe), du dossier d'install, du MSI, du bundle .app et du
            // paquet .deb. L'identifiant technique reverse-DNS reste porté par le bundleID macOS.
            packageName = "bingobreed"
            packageVersion = version.toString()

            // Modules JDK à garder dans le runtime jlink (liste donnée par
            // `./gradlew :desktopApp:suggestRuntimeModules`). Sans eux le lanceur packagé sort en
            // code 1 sans fenêtre : `java.net.http` porte le HttpClient de DofusConfigProvider et
            // `jdk.unsupported` le sun.misc.Unsafe dont dépendent JNA (pcap4j) et protobuf.
            modules("java.instrument", "java.naming", "java.net.http", "java.sql", "jdk.unsupported")

            // Icônes de distribution : un format natif par OS, tous dérivés du même master
            // (cf. icons/generate-icons.ps1). L'icône de la *fenêtre* est posée à part dans main.kt.
            windows { iconFile.set(project.file("icons/bingobreed.ico")) }
            macOS {
                iconFile.set(project.file("icons/bingobreed.icns"))
                bundleID = "fr.lilone.bingobreed"
            }
            linux { iconFile.set(project.file("icons/bingobreed.png")) }
        }
    }
}