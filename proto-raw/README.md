# Module :proto-raw

Schéma **brut** du protocole de jeu, extrait du client via **protodec**.

Contrairement à `:proto` (protos « propres », noms réels), ce module ne génère
**aucune classe Java** : uniquement un `FileDescriptorSet` binaire (`dofus-raw.desc`)
bundlé en ressource. Le runtime résout les messages par `type_url` et les décode
en `DynamicMessage` (cf. `DescriptorRegistry` / `GameMessageDecoder` dans `shared`).

Pourquoi : les noms de messages d'`output.proto` = les codes des `type_url`
(`type.ankama.com/<code>`). Le décodage est donc **automatique** et **robuste aux
shuffles** de champs : il suffit de régénérer le descripteur à chaque patch.

## Mise à jour après un patch Dofus

1. Lancer **protodec** sur le client → `output.proto`.
2. Remplacer `src/main/proto/output.proto`.
3. Corriger les éventuelles sorties invalides de protodec (protoc le signalera).
   Problème connu : **types imbriqués référencés sans qualification**, ex.
   `repeated Group values = 1;` → `repeated Friend.Group values = 1;`.
4. `./gradlew :proto-raw:build` — si vert, le descripteur est régénéré et bundlé.

Aucun code applicatif à toucher : `type.ankama.com/<code>` → message `<code>` →
`DynamicMessage` (champs par numéro, layout courant).
