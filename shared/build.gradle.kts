plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            // Sniffer réseau (JVM uniquement)
            implementation(projects.proto)
            implementation(projects.protoRaw) // FileDescriptorSet d'output.proto (DynamicMessage)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.pcap4j.core)
            implementation(libs.pcap4j.packetfactoryStatic)
            api(libs.slf4j.api)
            implementation(libs.logback.classic)
        }
    }
}

// Propage le flag du downloader d'images hors-ligne (tools/MuldoImageDownloader) au JVM de test forké.
tasks.withType<Test>().configureEach {
    System.getProperty("downloadMuldoImages")?.let { systemProperty("downloadMuldoImages", it) }
}