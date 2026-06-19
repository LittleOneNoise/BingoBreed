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

## 2. Mapping wire courant — message `hlo`

> ⚠️ Section **périssable** : à resynchroniser après chaque patch. Le code
> (`hlo`) et les n° de champ ci-dessous datent de la dernière capture (2026-06).

| n° champ | nom obfusqué | sémantique               | notes |
|---------:|--------------|--------------------------|-------|
| 1        | `feai`       | ?                        | jamais vu `true` |
| 2        | `feaj`       | **nom**                  | seul champ string |
| 3        | `feal`       | ?                        | `repeated` enum (`hhb`) |
| 4        | `feam`       | **id d'apparence/robe**  | 91 / 96 (amande) / 97 (ivoire) / 101 (doré+pourpre) |
| 5        | `fean`       | **niveau**               | |
| 6        | `feao`       | **expérience**           | numérateur seul (le `/max` est UI/client) |
| 7        | `feap`       | **généalogie** (robes des 2 parents) | sous-msg `hlm` ; absent = robe de base / parents inconnus |
| 8        | `feaq`       | ?                        | enum (`hlk`) |
| 9        | `fear`       | **effets**               | voir §4 |
| 10       | `feas`       | **sérénité** (signée)    | présente **même si stérile** (l'UI l'affiche « indisponible ») |
| 11       | `feat`       | **stérile** (`true`)     | |
| 12       | `feau`       | **sexe mâle** (`true`=♂, absent=♀) | |
| 13       | `feav`       | **jauges monture**       | voir §3 |

Sous-messages :
- **généalogie** (`hlm`) : `feac`=1 (robe parent 1), `fead`=2 (robe parent 2), `feae`=3 inutilisé.
  Chaque valeur = id de robe dans un **espace propre aux parents, DISTINCT de celui de `feam`**
  (cf. `model/breeding/Robes.kt` : `PARENT_IDS` vs `OWN_IDS`). Preuve : Turquoise = `98` en robe
  propre mais `93` en parent ; « Roux et Doré » = `115` en propre, `120` en parent. Ordre
  `feac`/`fead` = parent 1 / parent 2 ; `feap` constant entre frères/sœurs.
- **jauge** (`hll`) : valeur=1 (`fdzx`), type=2 (`fdzy`, enum `hhd` 0/1/2).
- **effet** (`kiv`) : id=8 (`fppp`) ; valeur simple=3 (`fpps`) ; effet complexe=7 (`fppy`).

---

## 3. Jauges monture (`feav`) & fertilité — **dérivation client**

3 jauges, valeurs `0..20000`, indexées par l'enum `hhd` :
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

- `hlp` (vide) → `hlq` : **lister les enclos** ; `febe` = map `<index 1..6, déverrouillé>`.
- `hkv { index 1..6 }` : **sélectionner** un enclos (établit le contexte). Réponse `hle`.
- `hle` : **push du contenu** de l'enclos (`him` : 6 jauges `hhc` + map montures).
- Jauges de carburant d'enclos (`hhc`, confirmé) : 0 baffeur · 1 caresseur ·
  2 foudroyeur · 3 abreuvoir · 4 dragofesse · 5 mangeoire.
- Agir sur une jauge = **Request/Response corrélé** (`hhw` activer / `hki` désactiver,
  avec `uid`) ; ouvrir/sélectionner = **Events** (`uid` = -1).
