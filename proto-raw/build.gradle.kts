import com.google.protobuf.gradle.GenerateProtoTask

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.protobuf)
}

// Module "descriptor-only" : on ne génère AUCUNE classe Java pour output.proto
// (protodec, ~1470 messages obfusqués). On produit uniquement le FileDescriptorSet
// binaire, consommé au runtime via DynamicMessage (résolution par type_url).

dependencies {
    // Fournit les well-known protos (google/protobuf/any.proto…) sur le proto-path.
    implementation(libs.protobuf.java)
}

val descriptorFile = layout.buildDirectory.file("generated/descriptors/dofus-raw.desc")

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task: GenerateProtoTask ->
            // Désactive la génération Java : on ne garde que le descripteur.
            task.builtins.removeIf { it.name == "java" }
            task.generateDescriptorSet = true
            task.descriptorSetOptions.includeImports = true
            task.descriptorSetOptions.path = descriptorFile.get().asFile.absolutePath
        }
    }
}

// Embarque le .desc dans le jar (chargé au runtime depuis le classpath).
sourceSets {
    main {
        resources.srcDir(descriptorFile.get().asFile.parentFile)
    }
}

tasks.named("processResources") {
    dependsOn("generateProto")
}
