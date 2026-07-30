# Reconstruction du message **Monture** (élevage) — référence stable

> **Pourquoi ce doc ?** `output.proto` est régénéré par protodec à chaque patch Dofus :
> les noms obfusqués (`feaj`, `feam`, …), le code de message (`type.ankama.com/hlo`)
> et parfois les **numéros de champ** sont reshufflés. Les commentaires dans
> `output.proto` sont donc **perdus à chaque MAJ**.
>
> Ce fichier est la **mémoire stable** : structure sémantique + recette pour
> **ré-identifier** chaque champ après un shuffle. Le seul artefact à resynchroniser
> en code reste [`PaddockMapper`](PaddockMapper.kt) (numéros de champ).

---

## 1. Modèle sémantique (stable — ne bouge pas)

La monture telle qu'on la reconstitue (cf. [`Mount`](../../model/breeding/Paddock.kt)) :

| sémantique          | type            | statut        |
|---------------------|-----------------|---------------|
| nom                 | string optionnel| confirmé      |
| niveau              | int             | confirmé      |
| expérience          | int             | confirmé      |
| sérénité            | int **signé**   | confirmé      |
| stérile             | bool            | confirmé (`fomt`, cf. §2)|
| sexe (mâle)         | bool            | confirmé      |
| jauges (amour/maturité/endurance) | 3 × (type, valeur 0..20000) | confirmé (amour=type0) |
| effets              | liste (id, valeur) | confirmé   |
| id d'apparence/robe | int             | confirmé (cf. §2, preuve par recette de clonage) |
| **généalogie (robes des 2 parents)** | 2 × id de robe | confirmé (cf. §2) |
| **fertilité (3 états)** | dérivé        | **calcul client, pas un champ réseau** |
| génération          | —               | non isolée (corrélée à la robe ?) |

---

## 2. Mapping wire courant — message `hvf`

> ⚠️ Section **périssable** : à resynchroniser après chaque patch. Le code monture
> (**`hvf`**, ex-`htd`) et les n° de champ ci-dessous datent du **patch 2026-07-30**
> (refonte élevage : le message a été entièrement réordonné). Le contenu d'enclos est porté
> par **`hrp`** (ex-`hqm`) : `foau`=1 (map `<string, hvf>` montures), `foav`=2 (éléments actifs,
> `repeated hqt`, packed), `foaw`=3 (jauges carburant `huh` : **élément `fojn`=1, valeur `fojo`=2**
> — ordre re-inversé vs 2026-07).

| n° champ | nom obfusqué | sémantique               | notes |
|---------:|--------------|--------------------------|-------|
| 1        | `foms`       | ?                        | bool, jamais vu `true` |
| 2        | `fomt`       | **stérile** (`true`)     | n'apparaît QUE sur des montures aux 3 jauges à 20000 — c'est la **signature de la stérilité** (§3), pas d'un « prête à se reproduire » |
| 3        | `fomu`       | **jauges monture**       | `repeated hvc`, voir §3 |
| 4        | `fomv`       | **généalogie** (robes des 2 parents) | sous-msg `hvd` ; absent = robe gen 1 / parents inconnus |
| 5        | `fomw`       | ?                        | `repeated hqs` — jamais vu renseigné |
| 6        | `fomx`       | **niveau**               | 1 sur clone/nouveau-né, 53..85 sur adultes |
| 7        | `fomy`       | **sérénité** (signée)    | seul int dans ±5000 (vu à −4877) |
| 8        | `fomz`       | **nom**                  | seul champ string |
| 9        | `fonb`       | **effets**               | `repeated ldn`, voir §4 |
| 10       | `fonc`       | **sexe mâle** (`true`=♂, absent=♀) | seul bool qui **diffère entre les 2 parents** d'un accouplement |
| 11       | `fond`       | **expérience**           | absent sur clone/nouveau-né |
| 12       | `fone`       | **id d'apparence/robe**  | 90 (orchidée) / 93 (pourpre) / 98 (turquoise) / 140 (turquoise et pourpre) |

Sous-messages :
- **généalogie** (`hvd`) : `fomn`=1 (robe parent 1), `fomo`=2 (robe parent 2).
  Chaque valeur = id de robe dans le **même espace que `fone`** (table unique
  `model/breeding/Robes.kt`/`MuldoRobes.kt`).
  **Preuve 2026-07-30** : un clone de généalogie `{93 Pourpre, 98 Turquoise}` sort à
  `fone = 140` = « Turquoise et Pourpre », exactement la recette — ce qui fixe d'un coup
  `fone` (robe) **et** `fomx` (niveau), les deux plages se chevauchant sinon.
- **jauge** (`hvc`) : **valeur=1 (`fomh`), type=2 (`fomi`, enum `hqu` 0/1/2)** ← ordre valeur/type,
  re-inversé vs 2026-07.
- **effet** (`ldn`) : id=9 (`gbce`) ; valeur simple=8 (`gbcn`) ; effet complexe=7 (`gbcm`).

---

## 3. Jauges monture (`hvf.fomu`) & fertilité — **dérivation client**

3 jauges, valeurs `0..20000`, indexées par l'enum `hqu` (ex-`hhd`, ordinaux conservés) :
- `type 0` = **amour** (confirmé : bas chez les jeunes, pilote la reproduction)
- `type 1` = **maturité** ; `type 2` = **endurance** (confirmé en jeu)

La **fertilité** n'est **pas** un champ réseau — elle se calcule :

```
si stérile (fomt=true)              → STÉRILE   (jauges souvent max mais épuisée)
sinon si les 3 jauges == 20000      → FÉCONDE   (prête à se reproduire)
sinon                               → FERTILE   (encore en maturation)
```

Preuve : **stérile** et **féconde** ont toutes deux les 3 jauges à 20000 ; seul
`fomt` les départage. Donc « jauges au max » seul ≠ féconde.

> ⚠️ **Piège de ré-identification** (vécu au patch 2026-07-30) : parce que la stérilité
> n'apparaît QUE sur des jauges au max, le flag stérile **ressemble** à un flag serveur
> « féconde / prête à se reproduire ». Les deux hypothèses sont indistinguables sur un
> échantillon où aucune monture n'est réellement féconde-non-stérile — c'est le cas des
> parents d'un `huu` (ils viennent de se reproduire) et des montures rangées en certificat.
> **Trancher par l'UI** : une monture affichée « stérile » en jeu, et voir si le bool est là.

---

## 4. Effets (`hvf.fonb`) — ids Dofus

Chaque effet = `(id, valeur)`. Ids **visibles** rencontrés :

| id  | effet              |
|-----|--------------------|
| 128 | PM                 |
| 138 | Puissance          |
| 160 | Esquive PA         |
| 210 | Résistance Terre % |
| 212 | Résistance élément % (élément à confirmer) |
| 752 | Fuite              |

Ids **internes** présents sur **toute** monture (métadonnées d'élevage, non
affichés en tooltip) : `2819`, `2821`, `3829`, `3830`, `3831` (val. -10000),
`3833`, `3834`, `3835`. Les effets complexes utilisent `gbcm` (sous-message),
pas `gbcn` → valeur simple absente.

---

## 5. Recette de ré-identification après un patch

Quand les noms/numéros bougent, re-mapper en s'ancrant sur la **vérité-terrain UI**
d'une monture connue :

| champ      | comment le re-trouver |
|------------|------------------------|
| nom        | seul champ `string` du message |
| niveau / xp| `int` qui matchent les nombres UI ; xp **absent** sur un nouveau-né, niveau = 1 |
| sérénité   | `int` **signé** qui matche l'UI (tester une monture à sérénité négative) |
| stérile    | `bool` `true` **uniquement** sur une monture UI « stérile » |
| sexe       | seul `bool` qui **diffère entre les 2 parents** d'un accouplement (`huu`) |
| jauges     | `repeated` de 3 sous-msg à valeurs `0..20000` ; amour = celui bas chez un jeune |
| effets     | `repeated` (id, valeur) ; recouper les nombres du tooltip pour fixer les ids |
| robe       | **cloner/croiser** deux robes de recette connue : l'enfant porte la robe attendue → départage robe et niveau, dont les plages se chevauchent |
| généalogie | sous-msg à 2 ids de robe, **identique entre frères/sœurs** (même couple de parents) ; sur un nouveau-né, = les robes des 2 parents du même message |

> Astuce : les messages **auto-corrélés** valent mieux qu'un relevé UI —
> `huu` (accouplement) contient les 2 parents **et** l'enfant dans un seul paquet :
> le sexe est le seul bool discriminant, la généalogie de l'enfant recoupe les robes
> des parents, et le niveau de l'enfant vaut 1. Un **mâle stérile** reste à capturer
> pour confirmer `stérile` indépendamment.

---

## 6. Contexte enclos (rappel)

> ⚠️ Codes resynchronisés au **patch 2026-07-30** (l'ouverture de l'écran d'élevage envoie
> deux requêtes vides, `hvb` puis `hud`).

- `hvb` (C→S, vide) → `htg` : **collection complète des montures** (étable) →
  `fofv`(1) → `hte.fofr`(1) `map<string, hvf>`. Une seule map désormais.
- `hud` (C→S, vide) → `hsk` : **lister les enclos** ; `fodi`(1) = map `<index 1..6, déverrouillé>`.
- `hrw { fobk=2 : index 1..6 }` : **sélectionner** un enclos. Réponse `huz` →
  `fols`(1) → `hux.foln`(2) → `hrp`.
- `hso` : **push de mise à jour** de l'enclos → `foea`(1) → `hrp`.
- Jauges de carburant d'enclos (enum `hqt`, ex-`hhc`, **ordinaux conservés** — reconfirmé
  2026-07-30) : 0 baffeur · 1 caresseur · 2 foudroyeur · 3 abreuvoir · 4 dragofesse · 5 mangeoire.
- Agir sur une jauge = **Request/Response corrélé** (`hug` activer / `hrm` désactiver, avec `uid`) ;
  ouvrir/sélectionner = **Events** (`uid` = -1). La réponse `hrg` → `fnzm`(1) → `hre.fnzi`(1)
  liste les jauges **auto-désactivées** par le serveur (règles « 1 sérénité » / « 2 max »).
- Transfert montures enclos↔étable : requête C→S `hsy { fofh=2 : uuids, fofi=3 : sens }`,
  réponse S→C `hub.foir`(1) = map `<uuid, code>`.
- Enclos ↔ **inventaire** (certificat) : C→S `hsx { fofb=1 : uuids }` → S→C `hul.foke`(1)
  map `<uuid, code>` (`HUI_EFJD`=0 = OK) ; retour C→S `hti { fogi=1 : uid d'objet }` → S→C
  `hrk.fnzy`(1) map `<uid, hri{fnzt=1 uuid, fnzu=2 hvf}>` — la monture revient **complète**.
- Accouplement : C→S `hsp { foej=3, foek=4 : uuids des 2 parents }` → S→C `huu` → `folf`(1) →
  `hus` : parents `foku`(2), nouveau-né `fokv`(3), tous deux `map<string, hvf>`.
- Clonage : C→S `hth { fogc=1, foge=3 }` → S→C `hvm` → `font`(1) → `hvk { fonl=1 hvf, fonm=2 uuid }`.
- **Sonde de probabilités** (bébés hypothétiques) : C→S `hum { fokl=2, fokm=3 : uuids }` → S→C
  `htx` → `foik`(1) → `hto` : `fohf`(4) = `repeated hts { fohv=3 : id de robe, fohw=4 : float % }`.
  Sert à valider `BreedingProbability` contre le serveur — non consommé par l'app.

---

## 7. Succès d'élevage (rappel)

- `mgj` (C→S) → `mfo.gfqo`(1) : **vue d'ensemble** (`repeated mgc`), non catégorisée.
- `mgi { gftl=1 : id de catégorie }` → `mfn.gfqk`(2) : **liste détaillée** d'une catégorie
  (`gfqj`(1) = en-tête `mfl`). Catégories : 119 élevage général · 78 dragodinde · 79 muldo · 80 volkorne.
- Succès `mgc` : `gfsm`=1 (id), `gfsp`=3 (`repeated mga` objectifs) ; `gfsn`(2) non identifié.
- Objectif `mga` : `gfsf`=1 (**cible**), `gfsg`=2 (**courant**, `optional` — absent = terminé),
  `gfsi`=3 (id). Recoupé sur un objectif en cours : `{1600, 1552, 11141}`.
- ⚠️ Le serveur **ne repousse pas** ces listes quand une naissance complète un objectif : BingoBreed
  valide donc l'objectif **côté client** à la naissance (cf. `SnifferEngine.registerBirths` et
  `AchievementObjective.locallyValidated`), sinon le planificateur re-viserait une robe déjà obtenue.
