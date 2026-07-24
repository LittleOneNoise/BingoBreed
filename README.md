Projet Kotlin Multiplatform ciblant le Desktop (JVM).

* [/shared](./shared/src) contient le code partagé entre applications Compose Multiplatform :
  - [commonMain](./shared/src/commonMain/kotlin) — code commun à toutes les cibles ;
  - les autres dossiers ciblent une plateforme donnée. La partie Desktop (JVM) — dont tout le
    sniffer réseau et l'UI — vit dans [jvmMain](./shared/src/jvmMain/kotlin).
* [/desktopApp](./desktopApp) est la coque exécutable : `main.kt`, la configuration de packaging
  et les [icônes](./desktopApp/icons).

### Lancer l'appli

Depuis le widget de run de l'IDE, ou en ligne de commande :

- Hot reload : `./gradlew :desktopApp:hotRun --auto`
- Lancement standard : `./gradlew :desktopApp:run`
- Mode diagnostic (dump des frames protobuf) : `./gradlew :desktopApp:run -Pdiagnostic`

### Lancer les tests

Bouton de run dans la gouttière de l'éditeur, ou :

- `./gradlew :shared:jvmTest`

---

## Compiler un exécutable

Le packaging passe par [`jpackage`](https://docs.oracle.com/en/java/javase/21/jpackage/) via le
plugin Compose Desktop : le résultat embarque son propre runtime Java, l'utilisateur final n'a donc
**pas** besoin d'un JDK installé.

**Prérequis** : un JDK **17 ou plus** (le projet est construit avec un JDK 21 — `./gradlew
:desktopApp:javaToolchains` liste ceux détectés). Aucun autre outil à installer.

⚠️ **`jpackage` ne fait pas de compilation croisée** : on ne peut produire un `.exe`/`.msi` que
*depuis* Windows, et un `.app`/`.dmg` que *depuis* macOS. Pour livrer les deux, il faut deux
machines (ou deux runners CI).

### Windows 11 → `.exe`

```powershell
.\gradlew.bat :desktopApp:createDistributable
```

Produit un dossier autonome (~140 Mo, runtime Java inclus) :

```
desktopApp\build\compose\binaries\main\app\fr.lilone.bingobreed\
    fr.lilone.bingobreed.exe     ← le lanceur, avec l'icône embarquée
    app\                          ← jars + ressources
    runtime\                      ← JRE embarqué
```

⚠️ **Le `.exe` n'est pas autonome** : ce n'est qu'un lanceur (~660 Ko) qui démarre le JVM de
`runtime\` avec les jars de `app\`. Il faut distribuer **le dossier entier** (zip), ou passer par le
MSI ci-dessous. Pour le tester sans réinstaller : `.\gradlew.bat :desktopApp:runDistributable`.

Le runtime est produit par `jlink` et ne garde que les modules JDK déclarés dans
`nativeDistributions` — d'où le bloc `modules(…)` du `build.gradle.kts`. Si un jour l'appli sort en
**code 1 sans ouvrir de fenêtre** alors qu'elle tourne en `:desktopApp:run`, c'est le symptôme d'un
module manquant : `./gradlew :desktopApp:suggestRuntimeModules` redonne la liste à jour.

Pour un installeur MSI à la place :

```powershell
.\gradlew.bat :desktopApp:packageMsi
```

→ `desktopApp\build\compose\binaries\main\msi\fr.lilone.bingobreed-<version>.msi`

Le plugin Compose télécharge WiX tout seul au premier build (tâche `unzipWix`) — il faut donc une
connexion réseau, mais rien à installer à la main.

**À l'exécution**, sur la machine cible :
- [Npcap](https://npcap.com/#download) doit être installé (voir plus bas — sans lui, la capture ne
  démarre pas) ;
- l'appli doit être lancée **en administrateur**, sinon l'ouverture de l'interface réseau échoue.

### macOS → `.app` / `.dmg`

**Depuis un Mac** :

```bash
./gradlew :desktopApp:createDistributable   # bundle .app
./gradlew :desktopApp:packageDmg            # image disque
```

→ `desktopApp/build/compose/binaries/main/app/fr.lilone.bingobreed.app`
→ `desktopApp/build/compose/binaries/main/dmg/fr.lilone.bingobreed-<version>.dmg`

`jpackage` produit un binaire pour **l'architecture de la machine de build** : construire sur Apple
Silicon donne un bundle arm64, sur Intel un bundle x86_64.

Le bundle n'est ni signé ni notarisé : Gatekeeper le bloquera au premier lancement. Soit on lève la
mise en quarantaine à la main —

```bash
xattr -dr com.apple.quarantine /Applications/fr.lilone.bingobreed.app
```

— soit on renseigne un certificat Developer ID via le bloc `macOS { signing { … } }` de
[`desktopApp/build.gradle.kts`](./desktopApp/build.gradle.kts).

**À l'exécution** : macOS embarque libpcap nativement (pas d'équivalent Npcap à installer), mais
l'accès aux `/dev/bpf*` demande les droits root — il faut lancer via `sudo` ou installer le helper
*ChmodBPF* fourni avec Wireshark. À noter aussi que la détection du client Dofus
([`DofusConfigProvider.VERSION_FILE`](./shared/src/jvmMain/kotlin/fr/lilone/bingobreed/sniffer/config/DofusConfigProvider.kt))
pointe sur un chemin `%LOCALAPPDATA%` : sur macOS la version locale du client reste indéterminée et
le bandeau de version affiche « version locale illisible ».

### Linux → `.deb`

`./gradlew :desktopApp:packageDeb` (depuis Linux). Capture réseau : `sudo`, ou
`setcap cap_net_raw,cap_net_admin=eip` sur le lanceur.

### Limites connues du binaire packagé

- **Les logs atterrissent dans un dossier parasite.** `logback.xml` écrit dans
  `${bingobreed.projectDir}/logs/`, et cette propriété est injectée depuis `build.gradle.kts` avec le
  chemin du dépôt *de la machine de build*. `jpackage` en mange les antislashs, si bien que le
  fichier `.cfg` du binaire contient `-Dbingobreed.projectDir=D:GithubBingoBreed` : un chemin
  **relatif au lecteur**, qui fait créer un `<répertoire courant>\GithubBingoBreed\logs\` à chaque
  lancement. À traiter avant toute vraie distribution — soit en logguant dans un dossier utilisateur,
  soit en réservant ce `jvmArg` à la tâche `run` (le `${…:-.}` de `logback.xml` prend alors le relais).
- **Les tâches `packageRelease*`** (variantes minifiées par ProGuard) ne sont pas configurées. Le
  décodage protobuf repose sur `DynamicMessage` et la réflexion : sans règles `-keep` dédiées, la
  version minifiée cassera silencieusement au parsing.

## Icônes

Tout est dérivé d'un seul master, [`desktopApp/icons/source.png`](./desktopApp/icons/source.png)
(PNG carré 2048 px) :

| Fichier | Usage |
| --- | --- |
| `icons/bingobreed.ico` | icône de l'`.exe` et du MSI (16 → 256 px) |
| `icons/bingobreed.icns` | bundle `.app` et DMG |
| `icons/bingobreed.png` | paquet `.deb` (512 px) |
| `src/main/resources/icon.png` | icône de fenêtre / barre des tâches au runtime (256 px) |

Pour régénérer les quatre après avoir remplacé le master :

```powershell
pwsh -File desktopApp\icons\generate-icons.ps1
```

Le script n'utilise que `System.Drawing` (fourni avec Windows) — pas besoin d'ImageMagick.

## Onglet Debug : miroir de la console

L'onglet **Debug** affiche les lignes logback telles qu'elles partent vers la console — plus besoin
de garder un terminal ouvert à côté du jeu. Le flux est alimenté par
[`LogMirrorAppender`](./shared/src/jvmMain/kotlin/fr/lilone/bingobreed/log/LogMirrorAppender.kt),
branché dans `logback.xml` au même titre que les appenders console et fichier.

- **Tampon circulaire de 3000 lignes**, porté par le `replay` d'un `SharedFlow` : ouvrir l'onglet
  après coup rejoue l'historique, y compris les échecs survenus pendant le bootstrap.
- **Filtres** cumulables : chips de niveau, sous-chaîne sur le nom du logger, recherche dans le
  message. Ils ne jouent que sur l'affichage — le tampon capte tout.
- **Le TRACE n'existe que dans cette vue.** Le logger `fr.lilone.bingobreed` émet en TRACE, mais les
  appenders console et fichier sont plafonnés à DEBUG par un `ThresholdFilter`. C'est ce niveau qui
  porte les frames réseau brutes (`[CONN] frame 412b`, `[GAME …] frame 1024b`) : illisible dans un
  terminal, exploitable dès qu'on peut filtrer.
- Les stacktraces sont repliées derrière un `▸ N lignes de stacktrace` pour qu'une erreur ne noie
  pas le flux.

Le débit sous TRACE atteint plusieurs centaines de lignes par seconde : la collecte se fait par
paquets toutes les 80 ms pour éviter une recomposition par ligne.

## Npcap : que se passe-t-il s'il est absent ?

L'appli **démarre et reste utilisable**, mais aucune donnée n'arrive — l'échec est capturé, jamais
propagé, et l'UI ne le signale que dans l'onglet Debug :

1. `NpcapNativeSetup.ensureOnLibraryPath()` ne trouve pas `C:\Windows\System32\Npcap` et loggue un
   `WARN` (« Dossier Npcap introuvable »), sans interrompre quoi que ce soit ;
2. le bootstrap continue jusqu'à `Pcaps.findAllDevs()`, dont l'initialisation native lève un
   `UnsatisfiedLinkError: Unable to load library 'wpcap'` ;
3. le `runCatching` de `SnifferEngine.start()` attrape ce `Throwable` : log `ERROR` dans
   `logs/sniffer.log` et émission d'un `SnifferEvent.Failure("bootstrap", …)` ;
4. la fenêtre s'ouvre normalement — pas de crash, pas de freeze. Les onglets restent vides, la
   pastille réseau affiche « Réseau … » indéfiniment, et l'overlay « Signal perdu » ne se déclenche
   **pas** (il se base sur l'âge du dernier paquet reçu, qui n'existe jamais). Seul l'onglet
   **Debug** montre la ligne `ERROR … Échec [bootstrap]: Unable to load library 'wpcap'`, avec sa
   stacktrace dépliable.

Autrement dit : ni exception silencieuse ni plantage, mais un diagnostic peu visible. Les cas
voisins se comportent pareil (échec capté, remonté en `Failure`, UI vide) :

- **Npcap installé sans le mode « WinPcap API-compatible »** — c'est précisément ce que
  `NpcapNativeSetup` corrige, en ajoutant le dossier Npcap à `jna.library.path` ;
- **Npcap installé mais appli lancée sans droits admin** — `findAllDevs()` passe, c'est
  `openLive()` qui échoue plus tard avec une `PcapNativeException`.
