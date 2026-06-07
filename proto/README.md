# Module :proto

Schémas protobuf Dofus et classes Java générées (protobuf-java).

## Comment ajouter les schémas

1. Dépose tes fichiers `.proto` dans `src/main/proto/` (sous-dossiers libres).
2. Supprime `placeholder.proto`.
3. Build : `./gradlew :proto:build` → les classes Java sont générées dans
   `build/generated/source/proto/main/java/` et compilées dans le jar du module.
4. `shared` dépend déjà de `:proto`, donc les classes (ex. `LoginMessage`,
   `GameMessage`) sont disponibles dans le code du sniffer.

## Convention

Mets `option java_package = "...";` et `option java_multiple_files = true;` dans
chaque `.proto` pour des classes propres (une par message).
