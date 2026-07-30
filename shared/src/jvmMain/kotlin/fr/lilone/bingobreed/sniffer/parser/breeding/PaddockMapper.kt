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
     * Resynchronisés au patch **2026-07-30**. Les noms obfusqués sont relevés dans les
     * logs de diagnostic puis croisés avec `output.proto` pour leurs numéros de champ.
     */
    const val CODE_CONTENT = "huz"        // S→C : contenu d'enclos sur sélection (huz.fols(1)→hux.foln(2)=hrp)
    const val CODE_UPDATE = "hso"         // S→C : push de mise à jour de l'enclos (hso.foea(1)=hrp)
    const val CODE_SELECT = "hrw"         // C→S : sélection d'un enclos (porte l'index, hrw.fobk(2) int32)
    const val CODE_TRANSFER = "hub"       // S→C : changement d'emplacement de montures (hub.foir map<uuid,hty>)
    const val CODE_STABLE = "htg"         // S→C : collection complète des montures (htg.fofv(1)→hte.fofr(1))
    const val CODE_GAUGE_ON = "hug"       // C→S : activer une jauge (hug.foji hqt)
    const val CODE_GAUGE_OFF = "hrm"      // C→S : désactiver une jauge (hrm.foah hqt)
    const val CODE_GAUGE_ON_RESP = "hrg"  // S→C : réponse d'activation (jauges auto-désactivées)
    const val CODE_BREED = "hsp"          // C→S : clic « Accoupler » (porte les 2 UUID parents, hsp.foej/foek)
    const val CODE_BREED_RESULT = "huu"   // S→C : résultat d'accouplement (parents à jour + nouveau-né)
    const val CODE_CLONE_RESULT = "hvm"   // S→C : monture obtenue par clonage (« dupliquer »)
    const val CODE_PADDOCK_LIST = "hsk"   // S→C : état verrouillé/déverrouillé des 6 enclos, event poussé à
                                          // l'ouverture de l'écran d'élevage (déclenché par `hud`, requête vide)
    const val CODE_TO_INVENTORY = "hul"   // S→C : montures **sorties vers l'inventaire** (certificat),
                                          // réponse à `hsx` (C→S, liste d'UUID)
    const val CODE_FROM_INVENTORY = "hrk" // S→C : montures **remises en enclos depuis l'inventaire**,
                                          // réponse à `hti` (C→S, liste d'uid d'objet)

    /**
     * Numéros de champ wire — UNIQUE point de resynchronisation après un patch. Noms **sémantiques**
     * (jamais à renommer) ; ne mettre à jour que la **valeur** et le **commentaire d'identité**
     * (`message.champ`). Identité courante : patch 2026-07-30 (refonte élevage : la monture n'est plus
     * `htd` mais **`hvf`**, et le contenu d'enclos **`hrp`**).
     */
    private object F {
        // Contenu complet d'enclos : `huz`.fols(1) → hux.foln(2) → hrp. `huz` porte deux `hux` de même
        // forme (fols(1) dans le oneof folv, folr(2) hors oneof) : on lit le 1ᵉʳ présent.
        const val CONTENT_WRAPPER = 1     // huz.fols → hux (oneof folv ; folt(3) = code d'erreur huw)
        const val CONTENT_WRAPPER_ALT = 2 // huz.folr → hux (même type, hors oneof)
        const val CONTENT_BODY = 2        // hux.foln → hrp (jauges + montures ; folm(1) = code hqw)
        // Mise à jour d'enclos : `hso`.foea(1) → hrp
        const val UPDATE_BODY = 1         // hso.foea → hrp
        // Sélection d'enclos : `hrw`.fobk(2) int32 (index 1..6 ; oneof fobm, fobj(1)=hru)
        const val SELECT_INDEX = 2        // hrw.fobk
        // Transfert de montures : `hub`.foir(1) map<string,hty> (clé = UUID)
        const val TRANSFER_MAP = 1        // hub.foir
        // (Dés)activation d'une jauge : élément hqt
        const val GAUGE_ON_ELEMENT = 1    // hug.foji ✓ (capture 2026-07-30 : activer « baffeur »/sérénité
                                          // négative → foji=HQT_EEPV(0) ; fojj(2)=hue vide, rôle non identifié)
        const val GAUGE_OFF_ELEMENT = 1   // hrm.foah (champ unique) ✓
        // Accouplement : `hsp` porte les 2 UUID parents (string), foej(3) et foek(4)
        const val BREED_PARENT_1 = 3      // hsp.foej
        const val BREED_PARENT_2 = 4      // hsp.foek
        // Résultat d'accouplement : `huu`.folf(1) → hus ; parents foku(2), nouveau-né fokv(3) <string,hvf>
        const val BREED_RESULT_BODY = 1   // huu.folf → hus
        const val BREED_RESULT_PARENTS = 2 // hus.foku  map<string,hvf>
        const val BREED_RESULT_CHILD = 3  // hus.fokv  map<string,hvf>
        // Résultat de clonage : `hvm`.font(1) → hvk{fonl(1)=hvf, fonm(2)=uuid}
        const val CLONE_RESULT_BODY = 1   // hvm.font → hvk
        const val CLONE_UUID = 2          // hvk.fonm
        const val CLONE_MOUNT = 1         // hvk.fonl  hvf
        // Réponse d'activation (jauges auto-désactivées) : `hrg`.fnzm(1) → hre.fnzi(1) rep hqt
        const val GAUGE_RESP_BODY = 1     // hrg.fnzm → hre (oneof fnzp ; fnzn(2) = code d'erreur hrd)
        const val GAUGE_RESP_ELEMENTS = 1 // hre.fnzi
        // Collection montures : `htg`.fofv(1) → hte.fofr(1) map<string,hvf> (une seule map désormais)
        const val STABLE_WRAPPER = 1      // htg.fofv → hte (oneof fofy)
        const val STABLE_MOUNTS = 1       // hte.fofr
        // Liste des enclos : `hsk`.fodi(1) map<int32,bool> (index 1..6 → déverrouillé). Confirmé en jeu
        // (capture 2026-07-30 : 5 enclos à true, le 6ᵉ à false, cohérent avec le palier "tous les 40
        // niveaux d'éleveur").
        const val PADDOCK_UNLOCKED = 1    // hsk.fodi
        // Sortie vers l'inventaire : `hul`.foke(1) map<string,hui> (clé = UUID, valeur = code de
        // résultat ; HUI_EFJD(0) = succès observé en jeu)
        const val TO_INVENTORY_MAP = 1    // hul.foke
        const val TO_INVENTORY_OK = 0     // hui.HUI_EFJD
        // Retour depuis l'inventaire : `hrk`.fnzy(1) map<int32,hri> (clé = uid d'objet) ;
        // hri{fnzt(1)=uuid, fnzu(2)=hvf}
        const val FROM_INVENTORY_MAP = 1  // hrk.fnzy
        const val FROM_INVENTORY_UUID = 1 // hri.fnzt
        const val FROM_INVENTORY_MOUNT = 2 // hri.fnzu  hvf
        // Contenu d'enclos (hrp)
        const val MOUNTS = 1              // hrp.foau  map<string,hvf>
        const val ACTIVE_ELEMENTS = 2     // hrp.foav  rep hqt (packed)
        const val FUEL_GAUGES = 3         // hrp.foaw  rep huh
        // Jauge carburant (huh) — ⚠️ élément et valeur ont de nouveau échangé de place
        const val FUEL_ELEMENT = 1        // huh.fojn  hqt
        const val FUEL_VALUE = 2          // huh.fojo  int32 (0..100000)
        // Monture (hvf). Ints désambiguïsés par plage ; bools par sanity.
        const val MOUNT_GAUGES = 3        // hvf.fomu  rep hvc
        const val MOUNT_PARENTS = 4       // hvf.fomv (sous-msg hvd)
        const val MOUNT_LEVEL = 6         // hvf.fomx (1 sur clone/nouveau-né, 53..85 sur adultes ✓)
        const val MOUNT_SERENITY = 7      // hvf.fomy (int, peut être négatif — vu à -4877 ✓)
        const val MOUNT_NAME = 8          // hvf.fomz (seul string)
        const val MOUNT_EFFECTS = 9       // hvf.fonb  rep ldn
        const val MOUNT_SEX = 10          // hvf.fonc (true=un sexe ✓ : seul bool qui diffère entre les 2
                                          // parents d'un accouplement)
        const val MOUNT_XP = 11           // hvf.fond (absent sur clone/nouveau-né ✓)
        const val MOUNT_APPEARANCE = 12   // hvf.fone ✓ : un clone de parents {93 Pourpre, 98 Turquoise}
                                          // sort à 140 = « Turquoise et Pourpre », exactement la recette
        const val MOUNT_STERILE = 2       // hvf.fomt ✓ : `true` **uniquement** sur des montures aux 3
                                          // jauges à 20000 — signature de la stérilité (une stérile garde
                                          // ses jauges au max, cf. README §3), pas d'un « prête à se
                                          // reproduire ». Confirmé en jeu : une monture stérile in-game
                                          // ressortait fertile quand on lisait `foms`(1) à la place.
                                          // `foms`(1) reste non identifié (jamais vu `true`).
        // Généalogie (hvd) : ids de robe des 2 parents
        const val PARENT_1 = 1            // hvd.fomn
        const val PARENT_2 = 2            // hvd.fomo
        // Jauge monture (hvc) — ⚠️ valeur avant type
        const val MGAUGE_VALUE = 1        // hvc.fomh
        const val MGAUGE_TYPE = 2         // hvc.fomi  hqu
        // Effet (ldn)
        const val EFFECT_ID = 9           // ldn.gbce
        const val EFFECT_VALUE = 8        // ldn.gbcn (valeur simple, membre du oneof gbct ; complexe = gbcm(7))
        // Entrée de map protobuf (standard)
        const val MAP_KEY = 1
        const val MAP_VALUE = 2
    }

    /**
     * Issue d'un accouplement (`huu`) : l'état **à jour des 2 parents** (jauges consommées, stérilité
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
     * UUIDs des 2 montures **venant d'être accouplées** (clic « Accoupler », `hsp`), ou null si ce
     * n'est pas ce message. Les parents sont alors **consommés** (jauges remises à zéro côté jeu) :
     * l'appelant les retire du calcul du planificateur jusqu'à un état frais qui les rétablit.
     */
    fun bredPair(message: DecodedGameAny, dynamic: Message?): Set<String>? {
        if (message.code != CODE_BREED) return null
        val msg = dynamic ?: return null
        return setOfNotNull(msg.str(F.BREED_PARENT_1), msg.str(F.BREED_PARENT_2)).ifEmpty { null }
    }

    /**
     * Issue d'un **accouplement** (`huu`) : nouveau-né(s) + état **à jour des 2 parents** (post-repro :
     * jauges remises à zéro, stérilité éventuelle). null si ce n'est pas ce message. Sert à garder
     * l'étable fraîche sans attendre un nouveau push complet — `htg` (collection) n'arrive qu'à la
     * 1ʳᵉ ouverture de l'interface d'élevage.
     */
    fun bredOffspring(message: DecodedGameAny, dynamic: Message?): BreedOutcome? {
        if (message.code != CODE_BREED_RESULT) return null
        val body = dynamic?.msg(F.BREED_RESULT_BODY) ?: return null
        val parents = body.mountMap(F.BREED_RESULT_PARENTS)
        val newborns = body.mountMap(F.BREED_RESULT_CHILD)
        if (parents.isEmpty() && newborns.isEmpty()) return null
        return BreedOutcome(parents, newborns)
    }

    /**
     * Monture obtenue par **clonage** (`hvm`) indexée par UUID, ou null si autre message. Comme
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
     * Montures **sorties de l'enclos vers l'inventaire** (mises en certificat, `hul`) : leurs UUIDs,
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
     * Montures **remises en enclos depuis l'inventaire** (certificat consommé, `hrk`) indexées par
     * UUID, ou null si autre message. La monture arrive **complète** (le message porte tout le `hvf`),
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

    /** Lit une `map<string, hvf>` (champ [n]) en montures indexées par UUID. */
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
