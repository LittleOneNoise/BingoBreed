plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.protobuf)
}

dependencies {
    // Exposé en `api` : les classes générées (LoginMessage, etc.) doivent être
    // visibles des modules consommateurs (shared).
    api(libs.protobuf.java)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    // Génération Java standard pour les protos "propres" (login/common/game_message).
}
