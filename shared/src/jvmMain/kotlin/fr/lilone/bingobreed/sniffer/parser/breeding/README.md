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
| stérile             | bool            | confirmé (n=1)|
| sexe (mâle)         | bool            | confirmé (n=1)|
| jauges (amour/maturité/endurance) | 3 × (type, valeur 0..20000) | confirmé (amour=type0) |
| effets              | liste (id, valeur) | confirmé   |
| id d'apparence/robe | int             | observé (encode espèce/gen/couleur) |
| **généalogie (robes des 2 parents)** | 2 × id de robe | **confirmé via `feap`** (cf. §2) |
| **fertilité (3 états)** | dérivé        | **calcul client, pas un champ réseau** |
| génération          | —               | non isolée (corrélée à la robe ?) |

---

## 2. Mapping wire courant — message `hsx`

> ⚠️ Section **périssable** : à resynchroniser après chaque patch. Le code monture
> (`hsx`) et les n° de champ ci-dessous datent du **patch 2026-06**. Le contenu d'enclos
> est porté par `hrk` → `fnke`(3) → `fnjw`(2) → **`htu`** (l'ex-`him`).
>
> `htu` : `fnsl`=2 (jauges carburant `hrm` : valeur `fnkn`=2, élément `fnko`=3),
> `fnsn`=4 (éléments actifs, `repeated hpd`), `fnso`=5 (map `<string, hsx>` montures).

| n° champ | nom obfusqué | sémantique               | notes |
|---------:|--------------|--------------------------|-------|
| 2        | `fnpi`       | **généalogie** (robes des 2 parents) | sous-msg `hsv` ; absent = robe gen 1 / parents inconnus |
| 3        | `fnpj`       | **id d'apparence/robe**  | 90 (orchidée) / 91 (ébène) / 95 (roux) / 97 (ivoire) |
| 4        | `fnpk`       | **nom**                  | seul champ string |
| 5        | `fnpm`       | **niveau**               | seul int ≤ 200 (désambiguïsation par plage) |
| 6        | `fnpn`       | ?                        | bool, jamais vu `true` |
| 7        | `fnpo`       | **sérénité** (signée)    | seul int dans ±5000 |
| 8        | `fnpp`       | **sexe mâle** (`true`=♂, absent=♀) | seul mapping bool donnant 2 sexes |
| 9        | `fnpq`       | **stérile** (`true`)     | confirmé : n'apparaît QUE sur des montures aux 3 jauges à 20000 |
| 10       | `fnpr`       | **jauges monture**       | voir §3 |
| 11       | `fnps`       | **expérience** (?)       | ⚠️ ≈ total de jauges — non critique, à reconfirmer |
| 13       | `fnpu`       | **effets**               | voir §4 |

Sous-messages :
- **généalogie** (`hsv`) : `fnpb`=1 (robe parent 1), `fnpc`=2 (robe parent 2).
  Chaque valeur = id de robe dans le **même espace que `fnpj`** (table unique
  `model/breeding/Robes.kt`/`MuldoRobes.kt`). Confirmé par la capture 2026-06 : une monture
  Ivoire (robe 97) a pour parents `115` (« Roux et Doré ») et `120` (« Ébène et Amande »),
  = exactement la recette d'Ivoire.
- **jauge** (`hsu`) : **type=1 (`fnow`, enum `hpe` 0/1/2), valeur=2 (`fnox`)** ← ordre type/valeur
  INVERSÉ vs l'ancien `hll`.
- **effet** (`lip`) : id=11 (`gbpd`) ; valeur simple=10 (`gbpo`) ; effet complexe=7 (`gbpl`).

---

## 3. Jauges monture (`feav`) & fertilité — **dérivation client**

3 jauges, valeurs `0..20000`, indexées par l'enum `hpe` (ex-`hhd`, ordinaux conservés) :
- `type 0` = **amour** (confirmé : bas chez les jeunes, pilote la reproduction)
- `type 1` = **maturité** ; `type 2` = **endurance** (confirmé en jeu)

La **fertilité** n'est **pas** un champ réseau — elle se calcule :

```
si stérile (feat=true)              → STÉRILE   (jauges souvent max mais épuisée)
sinon si les 3 jauges == 20000      → FÉCONDE   (prête à se reproduire)
sinon                               → FERTILE   (encore en maturation)
```

Preuve : **stérile** et **féconde** ont toutes deux les 3 jauges à 20000 ; seul
`feat` les départage. Donc « jauges au max » seul ≠ féconde.

---

## 4. Effets (`fear`) — ids Dofus

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
`3833`, `3834`, `3835`. Les effets complexes utilisent `fppy` (sous-message),
pas `fpps` → valeur simple absente.

---

## 5. Recette de ré-identification après un patch

Quand les noms/numéros bougent, re-mapper en s'ancrant sur la **vérité-terrain UI**
d'une monture connue :

| champ      | comment le re-trouver |
|------------|------------------------|
| nom        | seul champ `string` du message |
| niveau / xp| `int` qui matchent les nombres UI |
| sérénité   | `int` **signé** qui matche l'UI (tester une monture à sérénité négative) |
| stérile    | `bool` `true` **uniquement** sur une monture UI « stérile » |
| sexe       | `bool` `true` **uniquement** sur un mâle |
| jauges     | `repeated` de 3 sous-msg à valeurs `0..20000` ; amour = celui bas chez un jeune |
| effets     | `repeated` (id, valeur) ; recouper les nombres du tooltip pour fixer les ids |
| robe (`feam`) | varie d'une monture à l'autre **à niveau/gen/sérénité égaux** |
| généalogie (`feap`) | sous-msg à 2 ids de robe, **identique entre frères/sœurs** (même couple de parents) |

> Astuce : ouvrir un enclos avec **2 montures qui ne diffèrent que d'UN attribut**
> (sexe, fertilité, robe) isole le champe correspondant. Un **mâle stérile**
> reste à capturer pour confirmer `stérile`+`sexe` indépendamment (n=1 actuel).

---

## 6. Contexte enclos (rappel)

> ⚠️ Codes resynchronisés au **patch 2026-06** (anciens entre parenthèses).

- `htl` (vide, ex-`hlp`) → `hrd` (ex-`hlq`) : **lister les enclos** ; `fnjg` = map `<index 1..6, déverrouillé>`.
- `hsi { fnng=3 : index 1..6 }` (ex-`hkv`) : **sélectionner** un enclos. Réponse `hrk`.
- `hrk` (ex-`hiy`) : **push complet du contenu** → `fnke`(3)→`fnjw`(2)→`htu` (6 jauges `hpd` + map montures).
- `hpv` (ex-`hle`) : **push de mise à jour** de l'enclos → `fnfr`(2)→`htu`.
- `htf` (C→S, vide) → `hqp` (ex-`hhv`) : **contenu de l'étable** → `fnhu`(1)→`hqn` ; montures dans
  `fnho`(1) et/ou `fnhq`(3) (`map<string,hsx>`). Les deux maps sont lues.
- Jauges de carburant d'enclos (enum `hpd`, ex-`hhc`, ordinaux conservés) : 0 baffeur · 1 caresseur ·
  2 foudroyeur · 3 abreuvoir · 4 dragofesse · 5 mangeoire.
- Agir sur une jauge = **Request/Response corrélé** (`hse` activer / `hsr` désactiver, ex-`hhw`/`hki`,
  avec `uid`) ; ouvrir/sélectionner = **Events** (`uid` = -1).
- Transfert montures enclos↔étable : requête C→S `hrq`, réponse S→C `hqb` (ex-`hif`) =
  map `<uuid, emplacement>` (`fngm`=2).
- ⚠️ **Étable** (ex-`hhv`) : code/structure **non recapturés** post-patch — `stableMounts` inactif
  jusqu'à une capture d'ouverture d'étable.
