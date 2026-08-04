package fr.lilone.bingobreed.sniffer.parser.breeding

import com.google.protobuf.Descriptors
import com.google.protobuf.Message
import fr.lilone.bingobreed.sniffer.model.DecodedGameAny
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.FuelGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountEffect
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Transforme le [Message] (DynamicMessage) d'un message d'élevage en [Paddock] / montures.
 *
 * ⚠️ **Couche fragile au patch.** Toute l'identité **wire** obfusquée (codes `type_url` 3 lettres
 * dans [CODE_CONTENT] etc., numéros de champ dans [F]) est isolée ici : c'est le **seul** endroit
 * à resynchroniser après une MAJ Dofus. Les **noms** de constantes sont **sémantiques et stables**
 * (ex. [F.MOUNTS], pas `HTU_MOUNTS`) → un patch ne change que des **valeurs** + leur commentaire
 * d'identité, jamais le corps des mappers ni les appelants. Le reste (modèle de domaine,
 * descripteur, DynamicMessage) est robuste.
 */
object PaddockMapper {

    /**
     * Codes type_url (sans préfixe `type.ankama.com/`). À ré-identifier après patch.
     * Resynchronisés au patch **2026-08-04**. Les noms obfusqués sont relevés dans les
     * logs de diagnostic puis croisés avec `output.proto` pour leurs numéros de champ.
     * Les codes marqués **(candidat)** n'ont pas été revus dans la capture 2026-08-04 :
     * ils viennent d'un appariement de **structure** avec l'ancien message et restent à
     * confirmer par l'action en jeu correspondante.
     */
    const val CODE_CONTENT = "hrc"        // S→C : contenu d'enclos sur sélection (hrc.fnip(2)→hra.fnig(2)=hta)
                                          // (candidat : même forme que l'ancien `huz`)
    const val CODE_UPDATE = "hua"         // S→C : push de mise à jour de l'enclos (hua.fnsb(1)=hta) ✓
    const val CODE_SELECT = "hrl"         // C→S : sélection d'un enclos (porte l'index, hrl.fnjk(2) int32)
                                          // (candidat : oneof {message vide, int32} comme l'ancien `hrw`)
    const val CODE_TRANSFER = "hur"       // S→C : changement d'emplacement de montures (hur.fnuc map<uuid,huo>) ✓
    const val CODE_STABLE = "huz"         // S→C : collection complète des montures (huz.fnuz(2)→hux.fnut(1)) ✓
    const val CODE_GAUGE_ON = "hun"       // C→S : activer une jauge (hun.fnto hqf) ✓
    const val CODE_GAUGE_OFF = "hqw"      // C→S : désactiver une jauge (hqw.fnhx hqf) ✓
    const val CODE_GAUGE_ON_RESP = "htr"  // S→C : réponse d'activation (jauges auto-désactivées) ✓
    const val CODE_BREED = "htu"          // C→S : clic « Accoupler » (porte les 2 UUID parents, htu.fnrd/fnrg) ✓
    const val CODE_BREED_RESULT = "hsu"   // S→C : résultat d'accouplement (parents à jour + nouveau-né) ✓
    const val CODE_CLONE_RESULT = "hsm"   // S→C : monture obtenue par clonage (« dupliquer ») ✓
    const val CODE_PADDOCK_LIST = "hus"   // S→C : état verrouillé/déverrouillé des 6 enclos, event poussé à
                                          // l'ouverture de l'écran d'élevage (candidat : seul `map<int32,bool>`
                                          // isolé du bloc élevage, forme de l'ancien `hsk`)
    const val CODE_TO_INVENTORY = "hti"   // S→C : montures **sorties vers l'inventaire** (certificat)
                                          // (candidat : map<uuid, code> comme l'ancien `hul`)
    const val CODE_FROM_INVENTORY = "hty" // S→C : montures **remises en enclos depuis l'inventaire**
                                          // (candidat : map<uid d'objet, {monture, uuid}> comme l'ancien `hrk`)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation après un patch. Noms **sémantiques**
     * (jamais à renommer) ; ne mettre à jour que la **valeur** et le **commentaire d'identité**
     * (`message.champ`). Identité courante : patch 2026-08-04 (la monture n'est plus `hvf` mais
     * **`hqu`**, et le contenu d'enclos **`hta`**).
     */
    private object F {
        // Contenu complet d'enclos : `hrc`.fnip(2) → hra.fnig(2) → hta. `hrc` porte deux `hra` de même
        // forme (fnip(2) dans le oneof fniq, fnim(4) hors oneof) : on lit le 1ᵉʳ présent.
        const val CONTENT_WRAPPER = 2     // hrc.fnip → hra (oneof fniq ; fnin(1) = code d'erreur hqz)
        const val CONTENT_WRAPPER_ALT = 4 // hrc.fnim → hra (même type, hors oneof)
        const val CONTENT_BODY = 2        // hra.fnig → hta (jauges + montures ; fnif(1) = code hqi)
        // Mise à jour d'enclos : `hua`.fnsb(1) → hta ✓ (capture 2026-08-04 : 6 jauges carburant,
        // élément actif [1] = caresseur, + la monture de l'enclos 1)
        const val UPDATE_BODY = 1         // hua.fnsb → hta
        // Sélection d'enclos : `hrl`.fnjk(2) int32 (index 1..6 ; oneof fnjm, fnjj(1) = hrj vide)
        const val SELECT_INDEX = 2        // hrl.fnjk
        // Transfert de montures : `hur`.fnuc(2) map<string,huo> (clé = UUID, valeur = code de résultat) ✓
        const val TRANSFER_MAP = 2        // hur.fnuc
        // (Dés)activation d'une jauge : élément hqf
        const val GAUGE_ON_ELEMENT = 1    // hun.fnto ✓ (capture 2026-08-04 : activer « baffeur »/sérénité
                                          // négative → message vide = HQF_EDZK(0))
        const val GAUGE_OFF_ELEMENT = 1   // hqw.fnhx (champ unique) ✓
        // Accouplement : `htu` porte les 2 UUID parents (string), fnrd(1) et fnrg(3) ✓
        const val BREED_PARENT_1 = 1      // htu.fnrd
        const val BREED_PARENT_2 = 3      // htu.fnrg
        // Résultat d'accouplement : `hsu`.fnnv(2) → hss ; ⚠️ parents et nouveau-né ont **échangé** de
        // champ : nouveau-né fnnk(2), parents fnnn(5), tous deux map<string,hqu>. Preuve (capture
        // 2026-08-04) : la monture de fnnk(2) est de niveau 1, sans XP, et sa généalogie {93, 90} vaut
        // exactement les robes des 2 montures de fnnn(5).
        const val BREED_RESULT_BODY = 2   // hsu.fnnv → hss (oneof fnnw ; fnnt(1) = code d'erreur hsp)
        const val BREED_RESULT_BODY_ALT = 3 // hsu.fnns → hss (même type, hors oneof)
        const val BREED_RESULT_PARENTS = 5 // hss.fnnn  map<string,hqu>
        const val BREED_RESULT_CHILD = 2  // hss.fnnk  map<string,hqu>
        // Résultat de clonage : `hsm`.fnmx(1) → hsk{fnmr(1)=hqu, fnms(2)=uuid} ✓
        const val CLONE_RESULT_BODY = 1   // hsm.fnmx → hsk (oneof fnna ; fnmy(3) = code d'erreur hsj)
        const val CLONE_UUID = 2          // hsk.fnms
        const val CLONE_MOUNT = 1         // hsk.fnmr  hqu
        // Réponse d'activation (jauges auto-désactivées) : `htr`.fnqw(2) → htp.fnqq(1) rep hqf ✓
        // (capture 2026-08-04 : activer la sérénité négative renvoie [1] = caresseur évincé)
        const val GAUGE_RESP_BODY = 2     // htr.fnqw → htp (oneof fnqz ; fnqx(3) = code d'erreur htn)
        const val GAUGE_RESP_ELEMENTS = 1 // htp.fnqq
        // Collection montures : `huz`.fnuz(2) → hux.fnut(1) map<string,hqu> ✓ (37 Ko à l'ouverture de
        // l'écran d'élevage, déclenché par `hqx` C→S vide)
        const val STABLE_WRAPPER = 2      // huz.fnuz → hux (oneof fnva ; fnux(1) = code d'erreur huw)
        const val STABLE_MOUNTS = 1       // hux.fnut
        // Liste des enclos : `hus`.fnug(1) map<int32,bool> (index 1..6 → déverrouillé)
        const val PADDOCK_UNLOCKED = 1    // hus.fnug
        // Sortie vers l'inventaire : `hti`.fnpu(1) map<string,htg> (clé = UUID, valeur = code de
        // résultat ; HTG_EEPS(0) = succès attendu)
        const val TO_INVENTORY_MAP = 1    // hti.fnpu
        const val TO_INVENTORY_OK = 0     // htg.HTG_EEPS
        // Retour depuis l'inventaire : `hty`.fnrr(1) map<int32,htw> (clé = uid d'objet) ;
        // htw{fnrm(1)=hqu, fnrn(2)=uuid} — ⚠️ monture et uuid **inversés** vs l'ancien `hri`
        const val FROM_INVENTORY_MAP = 1  // hty.fnrr
        const val FROM_INVENTORY_UUID = 2 // htw.fnrn
        const val FROM_INVENTORY_MOUNT = 1 // htw.fnrm  hqu
        // Contenu d'enclos (hta) — ⚠️ les 3 champs ont tourné (montures 1→3, jauges 3→1)
        const val MOUNTS = 3              // hta.fnpe  map<string,hqu>
        const val ACTIVE_ELEMENTS = 2     // hta.fnpd  rep hqf (packed)
        const val FUEL_GAUGES = 1         // hta.fnpc  rep htz
        // Jauge carburant (htz) — élément puis valeur (inchangé)
        const val FUEL_ELEMENT = 1        // htz.fnrw  hqf
        const val FUEL_VALUE = 2          // htz.fnrx  int32 (0..100000)
        // Monture (hqu). Ints désambiguïsés par plage ; bools par sanity.
        const val MOUNT_XP = 1            // hqu.fnhc (absent sur clone/nouveau-né ✓)
        const val MOUNT_EFFECTS = 2       // hqu.fnhd  rep lng
        const val MOUNT_LEVEL = 3         // hqu.fnhe (1 sur clone/nouveau-né, 62..83 sur adultes ✓)
        const val MOUNT_SEX = 4           // hqu.fnhf (true=un sexe ✓ : seul bool qui diffère entre les 2
                                          // parents d'un accouplement)
        const val MOUNT_NAME = 5          // hqu.fnhg (seul string, `optional`)
        const val MOUNT_GAUGES = 6        // hqu.fnhi  rep hqr
        const val MOUNT_PARENTS = 9       // hqu.fnhl (sous-msg hqs)
        const val MOUNT_STERILE = 10      // hqu.fnhm : `true` **uniquement** sur des montures aux 3 jauges
                                          // à 20000 — signature de la stérilité (une stérile garde ses
                                          // jauges au max, cf. README §3), pas d'un « prête à se
                                          // reproduire ». ⚠️ à reconfirmer sur une monture affichée
                                          // stérile en jeu : `fnhj`(7) est l'autre bool candidat (jamais
                                          // vu `true`). Ce **ne peut pas** être le sexe : les 2 parents
                                          // d'un même accouplement le portent tous les deux.
        const val MOUNT_SERENITY = 11     // hqu.fnhn (int, peut être négatif — vu à -2791 ✓)
        const val MOUNT_APPEARANCE = 12   // hqu.fnho ✓ : généalogie {95 Roux, 99 Prune} → robe 151
                                          // « Prune et Roux », et clone {96 Amande, 94 Doré} → 121
                                          // « Doré et Amande » : exactement les recettes
        // Généalogie (hqs) : ids de robe des 2 parents
        const val PARENT_1 = 1            // hqs.fngx
        const val PARENT_2 = 2            // hqs.fngy
        // Jauge monture (hqr) — valeur avant type, ⚠️ le type passe de 2 à 3 (fngs(2) = rep int32, inconnu)
        const val MGAUGE_VALUE = 1        // hqr.fngr
        const val MGAUGE_TYPE = 3         // hqr.fngt  hqg
        // Effet (lng)
        const val EFFECT_ID = 11          // lng.gcbz
        const val EFFECT_VALUE = 4        // lng.gccf (valeur simple, membre du oneof gcco ; complexe = gcci(6))
        // Entrée de map protobuf (standard)
        const val MAP_KEY = 1
        const val MAP_VALUE = 2
    }

    /**
     * Issue d'un accouplement (`hsu`) : l'état **à jour des 2 parents** (jauges consommées, stérilité
     * éventuelle) et le(s) **nouveau-né(s)**. Les deux sont séparés car seule une **naissance** valide
     * un objectif de succès (cf. `SnifferEngine.registerBirths`).
     */
    data class BreedOutcome(
        val parents: Map<String, Mount>,
        val newborns: Map<String, Mount>,
    ) {
        /** Toutes les montures du message, pour rafraîchir le registre étable d'un coup. */
        val all: Map<String, Mount> get() = parents + newborns
    }

    /** Dispatcher depuis un message de jeu décodé ; null si ce n'est pas un enclos. */
    fun fromGameMessage(message: DecodedGameAny, dynamic: Message?): Paddock? {
        val msg = dynamic ?: return null
        return when (message.code) {
            CODE_CONTENT -> (msg.msg(F.CONTENT_WRAPPER) ?: msg.msg(F.CONTENT_WRAPPER_ALT))
                ?.msg(F.CONTENT_BODY)?.let(::fromContent)
            CODE_UPDATE -> msg.msg(F.UPDATE_BODY)?.let(::fromContent)
            else -> null
        }
    }

    /** Index d'enclos (1..6) d'une requête de sélection, ou null si autre message. */
    fun selectedPaddockIndex(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_SELECT) return null
        return dynamic?.intOrNull(F.SELECT_INDEX)
    }

    /**
     * UUIDs des montures dont l'emplacement vient de changer (réponse de transfert), ou null si ce
     * n'est pas ce message. Le sens (entrée/sortie d'enclos) se déduit côté appelant via l'état.
     */
    fun transferredMountIds(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_TRANSFER) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.TRANSFER_MAP).mapNotNull { it.str(F.MAP_KEY) }.toSet().ifEmpty { null }
    }

    /**
     * UUIDs des 2 montures **venant d'être accouplées** (clic « Accoupler », `htu`), ou null si ce
     * n'est pas ce message. Les parents sont alors **consommés** (jauges remises à zéro côté jeu) :
     * l'appelant les retire du calcul du planificateur jusqu'à un état frais qui les rétablit.
     */
    fun bredPair(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_BREED) return null
        val msg = dynamic ?: return null
        return setOfNotNull(msg.str(F.BREED_PARENT_1), msg.str(F.BREED_PARENT_2)).ifEmpty { null }
    }

    /**
     * Issue d'un **accouplement** (`hsu`) : nouveau-né(s) + état **à jour des 2 parents** (post-repro :
     * jauges remises à zéro, stérilité éventuelle). null si ce n'est pas ce message. Sert à garder
     * l'étable fraîche sans attendre un nouveau push complet — `huz` (collection) n'arrive qu'à la
     * 1ʳᵉ ouverture de l'interface d'élevage.
     */
    fun bredOffspring(message: DecodedGameAny, dynamic: Message?): BreedOutcome? {
        if (message.code != CODE_BREED_RESULT) return null
        val msg = dynamic ?: return null
        val body = msg.msg(F.BREED_RESULT_BODY) ?: msg.msg(F.BREED_RESULT_BODY_ALT) ?: return null
        val parents = body.mountMap(F.BREED_RESULT_PARENTS)
        val newborns = body.mountMap(F.BREED_RESULT_CHILD)
        if (parents.isEmpty() && newborns.isEmpty()) return null
        return BreedOutcome(parents, newborns)
    }

    /**
     * Monture obtenue par **clonage** (`hsm`) indexée par UUID, ou null si autre message. Comme
     * [bredOffspring], maintient l'étable à jour entre deux push complets.
     */
    fun clonedMount(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_CLONE_RESULT) return null
        val body = dynamic?.msg(F.CLONE_RESULT_BODY) ?: return null
        val uuid = body.str(F.CLONE_UUID) ?: return null
        val value = body.msg(F.CLONE_MOUNT) ?: return null
        return mapOf(uuid to toMount(uuid, value))
    }

    /**
     * Montures **sorties de l'enclos vers l'inventaire** (mises en certificat, `hti`) : leurs UUIDs,
     * ou null si autre message. Elles quittent le cheptel exploitable — l'appelant les retire de
     * l'enclos **et** du registre étable, sinon le planificateur continuerait à compter dessus.
     * Seules les entrées au code de résultat « succès » sont retenues (un refus serveur ne déplace
     * rien en jeu).
     */
    fun mountsToInventory(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_TO_INVENTORY) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.TO_INVENTORY_MAP)
            .filter { it.enumNumber(F.MAP_VALUE) == F.TO_INVENTORY_OK }
            .mapNotNull { it.str(F.MAP_KEY) }
            .toSet()
            .ifEmpty { null }
    }

    /**
     * Montures **remises en enclos depuis l'inventaire** (certificat consommé, `hty`) indexées par
     * UUID, ou null si autre message. La monture arrive **complète** (le message porte tout le `hqu`),
     * donc pas besoin de la réinjecter depuis l'étable comme pour un simple transfert.
     */
    fun mountsFromInventory(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_FROM_INVENTORY) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.FROM_INVENTORY_MAP).mapNotNull { entry ->
            val slot = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
            val uuid = slot.str(F.FROM_INVENTORY_UUID) ?: return@mapNotNull null
            val value = slot.msg(F.FROM_INVENTORY_MOUNT) ?: return@mapNotNull null
            uuid to toMount(uuid, value)
        }.toMap().ifEmpty { null }
    }

    /** Élément de jauge venant d'être **activé**, ou null si autre message. */
    fun activatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_ON) return null
        return dynamic?.enumNumber(F.GAUGE_ON_ELEMENT)?.takeIf { it >= 0 }
    }

    /** Élément de jauge venant d'être **désactivé**, ou null si autre message. */
    fun deactivatedElement(message: DecodedGameAny, dynamic: Message?): Int? {
        if (message.code != CODE_GAUGE_OFF) return null
        return dynamic?.enumNumber(F.GAUGE_OFF_ELEMENT)?.takeIf { it >= 0 }
    }

    /**
     * Jauges **auto-désactivées** par le serveur suite à une activation : application des règles
     * « 1 jauge de sérénité » et « 2 jauges max » (éviction FIFO). Liste vide si rien n'a été
     * désactivé ; null si ce n'est pas la réponse d'activation.
     */
    fun autoDeactivatedElements(message: DecodedGameAny, dynamic: Message?): List<Int>? {
        if (message.code != CODE_GAUGE_ON_RESP) return null
        val body = dynamic?.msg(F.GAUGE_RESP_BODY) ?: return emptyList()
        return body.enumList(F.GAUGE_RESP_ELEMENTS)
    }

    /**
     * État verrouillé/déverrouillé des 6 enclos (`hsk`), indexé par numéro d'enclos (1..6) →
     * déverrouillé, ou null si ce n'est pas ce message. Seule source **fiable** du nombre d'enclos
     * débloqués : [fromGameMessage]/[paddocks] ne connaissent que les enclos déjà **ouverts** par le
     * joueur, ce qui sous-estime tant que tous les onglets n'ont pas été visités.
     */
    fun unlockedPaddocks(message: DecodedGameAny, dynamic: Message?): Map<Int, Boolean>? {
        if (message.code != CODE_PADDOCK_LIST) return null
        val msg = dynamic ?: return null
        return msg.messageList(F.PADDOCK_UNLOCKED)
            .associate { entry -> entry.int(F.MAP_KEY) to entry.bool(F.MAP_VALUE) }
            .ifEmpty { null }
    }

    /** Montures de l'étable indexées par UUID, ou null si autre message / étable absente. */
    fun stableMounts(message: DecodedGameAny, dynamic: Message?): Map<String, Mount>? {
        if (message.code != CODE_STABLE) return null
        val wrapper = dynamic?.msg(F.STABLE_WRAPPER) ?: return null
        return wrapper.mountMap(F.STABLE_MOUNTS).ifEmpty { null }
    }

    /** Cœur du mapping : le contenu d'enclos (jauges + montures) → [Paddock]. */
    fun fromContent(content: Message): Paddock = Paddock(
        activeElements = content.enumList(F.ACTIVE_ELEMENTS),
        fuelGauges = content.messageList(F.FUEL_GAUGES).map { gauge ->
            FuelGauge(element = gauge.enumNumber(F.FUEL_ELEMENT), value = gauge.int(F.FUEL_VALUE))
        },
        mounts = content.mountMap(F.MOUNTS),
    )

    /** Lit une `map<string, hqu>` (champ [n]) en montures indexées par UUID. */
    private fun Message.mountMap(n: Int): Map<String, Mount> = messageList(n).mapNotNull { entry ->
        val uuid = entry.str(F.MAP_KEY) ?: return@mapNotNull null
        val value = entry.msg(F.MAP_VALUE) ?: return@mapNotNull null
        uuid to toMount(uuid, value)
    }.toMap()

    private fun toMount(uuid: String, m: Message): Mount {
        val gauges = m.messageList(F.MOUNT_GAUGES).map { gauge ->
            MountGauge(type = gauge.enumNumber(F.MGAUGE_TYPE), value = gauge.int(F.MGAUGE_VALUE))
        }
        val sterile = m.bool(F.MOUNT_STERILE)
        return Mount(
            uuid = uuid,
            name = m.str(F.MOUNT_NAME),
            level = m.int(F.MOUNT_LEVEL),
            experience = m.int(F.MOUNT_XP),
            serenity = m.int(F.MOUNT_SERENITY),
            sex = if (m.bool(F.MOUNT_SEX)) Sex.MALE else Sex.FEMALE,
            sterile = sterile,
            fertility = Fertility.of(sterile, gauges),
            appearanceId = m.int(F.MOUNT_APPEARANCE),
            parents = m.msg(F.MOUNT_PARENTS)?.let { p ->
                listOfNotNull(p.intOrNull(F.PARENT_1), p.intOrNull(F.PARENT_2))
            } ?: emptyList(),
            gauges = gauges,
            effects = m.messageList(F.MOUNT_EFFECTS).map { effect ->
                MountEffect(effectId = effect.int(F.EFFECT_ID), value = effect.intOrNull(F.EFFECT_VALUE))
            },
        )
    }

    // --- Helpers de lecture DynamicMessage par numéro de champ ---

    private fun Message.field(n: Int): Descriptors.FieldDescriptor? =
        descriptorForType.findFieldByNumber(n)

    private fun Message.msg(n: Int): Message? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return getField(f) as? Message
    }

    private fun Message.int(n: Int): Int {
        val f = field(n) ?: return 0
        if (f.isRepeated) return 0
        return (getField(f) as? Number)?.toInt() ?: 0
    }

    /** Comme [int] mais null si le champ (souvent un membre de oneof) n'est pas présent. */
    private fun Message.intOrNull(n: Int): Int? {
        val f = field(n) ?: return null
        if (f.isRepeated || !hasField(f)) return null
        return (getField(f) as? Number)?.toInt()
    }

    /** Bool proto3 sans présence : getField renvoie le défaut (false) si absent. */
    private fun Message.bool(n: Int): Boolean {
        val f = field(n) ?: return false
        if (f.isRepeated) return false
        return (getField(f) as? Boolean) ?: false
    }

    private fun Message.str(n: Int): String? {
        val f = field(n) ?: return null
        if (f.isRepeated) return null
        return getField(f) as? String
    }

    private fun Message.enumNumber(n: Int): Int {
        val f = field(n) ?: return -1
        if (f.isRepeated) return -1
        return (getField(f) as? Descriptors.EnumValueDescriptor)?.number ?: -1
    }

    @Suppress("UNCHECKED_CAST")
    private fun Message.messageList(n: Int): List<Message> {
        val f = field(n) ?: return emptyList()
        if (!f.isRepeated) return emptyList()
        return getField(f) as List<Message>
    }

    private fun Message.enumList(n: Int): List<Int> {
        val f = field(n) ?: return emptyList()
        if (!f.isRepeated) return emptyList()
        return (getField(f) as List<*>).mapNotNull { (it as? Descriptors.EnumValueDescriptor)?.number }
    }
}
