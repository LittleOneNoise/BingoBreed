plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.protobuf)
}

dependencies {
    // Exposé en `api` : les classes générées (LoginMessage, GameMessage…) doivent
    // être visibles des modules consommateurs (shared).
    api(libs.protobuf.java)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    // Le générateur Java intégré est actif par défaut — rien d'autre à configurer.
    // Les .proto vont dans src/main/proto/, la sortie générée est ajoutée
    // automatiquement au sourceSet main.
}
