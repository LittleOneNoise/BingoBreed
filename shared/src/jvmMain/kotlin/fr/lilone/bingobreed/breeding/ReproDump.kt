package fr.lilone.bingobreed.breeding

import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.Robes
import fr.lilone.bingobreed.sniffer.model.breeding.Sex

/**
 * Vidage **texte brut** de l'état d'élevage (étable + enclos) et des directives du plan, pour copier
 * dans le presse-papier et le coller en debug. Volontairement verbeux côté identité de robe : pour
 * **chaque** monture on imprime l'`appearanceId` et ses deux résolutions — `Robes.robeName` (affichage)
 * **et** `MuldoRobes.BY_ID` (ce que consomme le planificateur via [OwnedStock]). Un `byId=NULL` explique
 * une monture vue à l'écran mais absente du plan (robe dont l'id réseau n'est pas encore relevé).
 */
object ReproDump {

    fun build(
        stable: Map<String, Mount>,
        paddocks: Map<Int?, Paddock>,
        plan: ReproPlan,
        remaining: List<String>,
    ): String = buildString {
        appendLine("=== BingoBreed dump ===")
        appendLine("remaining (${remaining.size}): ${remaining.joinToString(", ")}")
        appendLine()
        appendLine("ÉTABLE (${stable.size}):")
        stable.values.forEach { appendLine("  ${mountLine(it)}") }
        appendLine()
        paddocks.entries.sortedBy { it.key ?: Int.MAX_VALUE }.forEach { (id, p) ->
            appendLine("ENCLOS id=${id ?: "?"} active=${p.activeElements}:")
            appendLine("  fuel: ${p.fuelGauges.joinToString(", ") { "${it.element}:${it.value}" }}")
            appendLine("  mounts (${p.mounts.size}):")
            p.mounts.values.forEach { appendLine("    ${mountLine(it)}") }
            appendLine()
        }
        appendLine("PLAN (steps=${plan.steps.size}, justBred=${plan.justBred.size}, fullSuccess=${plan.fullSuccess}):")
        plan.steps.forEach { appendLine("  ${it.status} ${actionLine(it.action)}") }
        plan.justBred.forEach { appendLine("  JUST_BRED robe=\"${it.robe}\" name=\"${it.name ?: ""}\"") }
    }

    private fun mountLine(m: Mount): String {
        val byName = Robes.robeName(m.appearanceId)
        val byId = MuldoRobes.BY_ID[m.appearanceId]?.name
        val gen = Robes.robeGeneration(m.appearanceId)
        val g = m.gauges.joinToString(" ") { "${gaugeCode(it.type)}=${it.value}" }
        return "app=${m.appearanceId} robe=\"${byName ?: "?"}\" byId=\"${byId ?: "NULL"}\" gen=${gen ?: "?"} " +
            "${m.fertility} ster=${m.sterile} sex=${sx(m.sex)} niv=${m.level} ser=${m.serenity} " +
            "parents=${m.parents} g[$g] name=\"${m.name ?: ""}\""
    }

    private fun actionLine(a: NextAction): String = when (a) {
        is NextAction.Cross ->
            "Cross target=\"${a.target}\" gen=${a.targetGen} p=${(a.pSuccess * 100).toInt()}% " +
                "mother=${ownedRef(a.mother)} father=${ownedRef(a.father)}"
        is NextAction.RaiseGauges -> {
            val m = a.mount
            "RaiseGauges ${ownedRef(m)} missing=${m.gaugesMissing().map { gaugeCode(it.type) }} " +
                "raisableNow=${m.gaugesRaisableNow().map { gaugeCode(it.type) }} band=${m.serenityBand}"
        }
        is NextAction.Clone -> "Clone robe=\"${a.robe}\""
        is NextAction.Capture -> "Capture robe=\"${a.robe}\""
        is NextAction.NeedOppositeSex -> "NeedOppositeSex target=\"${a.target}\" have=${sx(a.have)}"
    }

    private fun ownedRef(m: OwnedMount): String =
        "\"${m.robe}\"(${sx(m.sex)},niv${m.level},ser${m.serenity},${m.fertility},${locStr(m.location)})"

    private fun locStr(loc: MountLocation): String = when (loc) {
        is MountLocation.Stable -> "étable"
        is MountLocation.Paddock -> "enclos${loc.id ?: "?"}"
    }

    private fun gaugeCode(type: Int): String = when (type) {
        MountGauge.TYPE_LOVE -> "L"
        MountGauge.TYPE_MATURITY -> "M"
        MountGauge.TYPE_ENDURANCE -> "E"
        else -> "?$type"
    }

    private fun sx(s: Sex): String = if (s == Sex.MALE) "M" else "F"
}
