# Correspondances proto obfusquées — patch 2026-07-30

Protocole : provoquer chaque action in-game, relever le code `type.ankama.com/<xxx>` dans les logs de
diagnostic, puis croiser avec `proto-raw/src/main/proto/output.proto` pour les numéros de champ.

Le code applicatif ne connaît **que** les constantes de
[`PaddockMapper`](shared/src/jvmMain/kotlin/fr/lilone/bingobreed/sniffer/parser/breeding/PaddockMapper.kt)
et [`AchievementMapper`](shared/src/jvmMain/kotlin/fr/lilone/bingobreed/sniffer/parser/breeding/AchievementMapper.kt) :
c'est le seul endroit à resynchroniser. Le détail sémantique de la monture vit dans
[`parser/breeding/README.md`](shared/src/jvmMain/kotlin/fr/lilone/bingobreed/sniffer/parser/breeding/README.md).

## Messages de structure (réutilisés partout)

| message | rôle | champs |
|---------|------|--------|
| `hvf` (ex-`htd`) | **monture** | `foms`=1 ? · `fomt`=2 **stérile** · `fomu`=3 jauges `hvc` · `fomv`=4 généalogie `hvd` · `fomx`=6 niveau · `fomy`=7 sérénité · `fomz`=8 nom · `fonb`=9 effets `ldn` · `fonc`=10 sexe · `fond`=11 xp · `fone`=12 robe |
| `hvc` | jauge monture | `fomh`=1 valeur · `fomi`=2 type (`hqu` : 0 amour, 1 maturité, 2 endurance) |
| `hvd` | généalogie | `fomn`=1 robe parent 1 · `fomo`=2 robe parent 2 |
| `ldn` (ex-`lgy`) | effet | `gbce`=9 id · `gbcn`=8 valeur simple · `gbcm`=7 valeur complexe |
| `hrp` (ex-`hqm`) | **contenu d'enclos** | `foau`=1 map montures · `foav`=2 éléments actifs (`hqt`, packed) · `foaw`=3 jauges carburant `huh` |
| `huh` | jauge carburant | `fojn`=1 élément (`hqt`) · `fojo`=2 valeur 0..100000 |
| `hqt` (ex-`hhc`) | élément d'enclos | 0 baffeur · 1 caresseur · 2 foudroyeur · 3 abreuvoir · 4 dragofesse · 5 mangeoire |
| `mgc` (ex-`mfi`) | **succès** | `gfsm`=1 id · `gfsp`=3 objectifs `mga` |
| `mga` (ex-`mfg`) | objectif | `gfsf`=1 cible · `gfsg`=2 courant (absent = terminé) · `gfsi`=3 id |

## Actions in-game → messages

### Ouverture de l'écran d'élevage
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hvb` | vide | — |
| S→C | `htg` | `fofv`(1) → `hte.fofr`(1) `map<string, hvf>` = **collection complète des montures** | `CODE_STABLE` |
| C→S | `hud` | vide | — |
| S→C | `hsk` | `fodi`(1) `map<int32, bool>` = **enclos déverrouillés** (1..5 true, 6 false) | `CODE_PADDOCK_LIST` |
| C→S | `hrw` | `fobk`(2) = index d'enclos sélectionné | `CODE_SELECT` |
| S→C | `huz` | `fols`(1) → `hux.foln`(2) → `hrp` = **contenu de l'enclos** | `CODE_CONTENT` |

### Actualisation des jauges / montures d'un enclos
| sens | code | contenu | mappé |
|------|------|---------|-------|
| S→C | `hso` | `foea`(1) → `hrp` | `CODE_UPDATE` |

### Jauges de sérénité
| action | sens | code | contenu | mappé |
|--------|------|------|---------|-------|
| activer | C→S | `hug` | `foji`(1) `hqt` — sérénité négative = élément **0** (baffeur) | `CODE_GAUGE_ON` |
| ↳ réponse | S→C | `hrg` | `fnzm`(1) → `hre.fnzi`(1) `repeated hqt` = jauges **auto-désactivées** ; sérénité positive évincée = élément **1** (caresseur) | `CODE_GAUGE_ON_RESP` |
| désactiver | C→S | `hrm` | `foah`(1) `hqt` | `CODE_GAUGE_OFF` |
| ↳ réponse | S→C | `hsb` | vide | — |

> Confirme au passage que l'enum `hqt` a conservé ses ordinaux (baffeur=0 / caresseur=1).

### Déplacer une monture étable ↔ enclos
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hsy` | `fofh`(2) uuids · `fofi`(3) sens (`hqv`, absent = vers l'enclos, 1 = vers l'étable) | — |
| S→C | `hub` | `foir`(1) `map<uuid, hty>` | `CODE_TRANSFER` |

### Déplacer une monture enclos ↔ inventaire (certificat) — **nouveau**
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hsx` | `fofb`(1) uuids | — |
| S→C | `hul` | `foke`(1) `map<uuid, hui>` (`HUI_EFJD`=0 = OK) | `CODE_TO_INVENTORY` |
| C→S | `hti` | `fogi`(1) uid(s) d'objet | — |
| S→C | `hrk` | `fnzy`(1) `map<uid, hri{fnzt=1 uuid, fnzu=2 hvf}>` — la monture revient **complète** | `CODE_FROM_INVENTORY` |

Effet côté app : sortie vers l'inventaire ⇒ retrait de l'enclos **et** du registre étable (ce n'est
plus une monture mais un objet) ; retour ⇒ insertion directe dans l'enclos actif.

### Cloner 2 montures
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hth` | `fogc`(1) · `foge`(3) uuids | — |
| S→C | `hvm` | `font`(1) → `hvk { fonl=1 hvf, fonm=2 uuid }` | `CODE_CLONE_RESULT` |

> **Ancrage du mapping robe/niveau** : ce clone a pour généalogie `{93 Pourpre, 98 Turquoise}` et
> sort à `fone = 140` = « Turquoise et Pourpre », exactement la recette → `fone` est bien la robe et
> `fomx` (=1 ici) le niveau, alors que leurs plages se chevauchent.

### Accoupler 2 montures
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hsp` | `foej`(3) · `foek`(4) uuids des 2 parents | `CODE_BREED` |
| S→C | `huu` | `folf`(1) → `hus` : parents `foku`(2), nouveau-né `fokv`(3), `map<string, hvf>` | `CODE_BREED_RESULT` |

> **Ancrage du mapping sexe** : `fonc`(10) est le seul bool qui diffère entre les 2 parents du même
> paquet.
>
> ⚠️ **Piège sur la stérilité** : `fomt`(2) vaut `true` exactement quand les 3 jauges sont à 20000
> (n=7), ce qui ressemble à un flag serveur « féconde ». C'est en réalité **la signature de la
> stérilité** (une stérile garde ses jauges au max — seul ce bool la départage d'une féconde). Les deux
> hypothèses sont indistinguables sur cet échantillon : les parents d'un `huu` viennent de se
> reproduire, et les montures rangées en certificat sont justement des stériles. Tranché par l'UI :
> `foms`(1) donnait « fertile » sur une monture stérile in-game. `foms`(1) reste non identifié.

### Calcul de bébés hypothétiques (sonde de probabilités)
| sens | code | contenu | mappé |
|------|------|---------|-------|
| C→S | `hum` | `fokl`(2) · `fokm`(3) uuids | — |
| S→C | `htx` | `foik`(1) → `hto` : `fohf`(4) `repeated hts { fohv=3 id de robe, fohw=4 float % }` · `fohe`(3) `htv` (kamas/xp) · `fohg`(5) nb de robes possibles | — |

Non consommé par l'app : sert à valider `BreedingProbability.calcP` contre le serveur.

### Succès
| action | sens | code | contenu | mappé |
|--------|------|------|---------|-------|
| ouvrir les succès | C→S | `mgj` | `gftp`(1) optional bool | — |
| ↳ vue d'ensemble | S→C | `mfo` | `gfqo`(1) `repeated mgc` | `CODE_LIST` |
| ouvrir une catégorie | C→S | `mgi` | `gftl`(1) = id de catégorie (119 général · 78 dragodinde · 79 muldo · 80 volkorne) | `CODE_CATEGORY_REQ` |
| ↳ liste détaillée | S→C | `mfn` | `gfqk`(2) `repeated mgc` (`gfqj`(1) = en-tête `mfl`) | `CODE_DETAILED` |

> ⚠️ Le serveur **ne repousse ni `mfn` ni `mfo`** quand une naissance complète un objectif : il faut
> rouvrir la catégorie en jeu. BingoBreed valide donc l'objectif **côté client** dès la naissance
> (`SnifferEngine.registerBirths` → `AchievementObjective.locallyValidated`), sinon le planificateur de
> repro re-viserait indéfiniment une robe déjà obtenue. Ces validations locales s'affichent en **or**
> (« née ici ») dans l'onglet Succès, pour que l'écart avec l'écran de succès in-game reste lisible.
