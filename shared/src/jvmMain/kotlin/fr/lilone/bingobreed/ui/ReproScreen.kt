package fr.lilone.bingobreed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.lilone.bingobreed.breeding.BreedingGenetics
import fr.lilone.bingobreed.breeding.ReproDump
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import fr.lilone.bingobreed.breeding.MountLocation
import fr.lilone.bingobreed.breeding.NextAction
import fr.lilone.bingobreed.breeding.OwnedMount
import fr.lilone.bingobreed.breeding.OwnedStock
import fr.lilone.bingobreed.breeding.ReproPlan
import fr.lilone.bingobreed.breeding.ReproPlanner
import fr.lilone.bingobreed.breeding.ReproTargets
import fr.lilone.bingobreed.breeding.StepStatus
import fr.lilone.bingobreed.sniffer.model.breeding.Achievement
import fr.lilone.bingobreed.sniffer.model.breeding.Fertility
import fr.lilone.bingobreed.sniffer.model.breeding.Mount
import fr.lilone.bingobreed.sniffer.model.breeding.MountGauge
import fr.lilone.bingobreed.sniffer.model.breeding.MuldoRobes
import fr.lilone.bingobreed.sniffer.model.breeding.Paddock
import fr.lilone.bingobreed.sniffer.model.breeding.SerenityBand
import fr.lilone.bingobreed.sniffer.model.breeding.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource

/**
 * Onglet Repro : planificateur « coach » vers le full succès Muldo. Calcule, à partir de
 * l'avancement des succès ([ReproTargets]) et du stock de montures (étable + enclos,
 * [OwnedStock]), la **prochaine action** concrète et la **cascade** restante ([ReproPlanner]).
 * Tout est recalculé à chaque recomposition (état live du sniffer). Muldo seul en v1.
 */
@Composable
fun ReproScreen(
    stable: Map<String, Mount>,
    paddocks: Map<Int?, Paddock>,
    consumed: Set<String>,
    achievements: Map<Int, Achievement>,
    unlockedPaddocks: Map<Int, Boolean>,
    now: Long,
    lastGameFrameAt: Long?,
) {
    if (!ReproTargets.hasMuldoData(achievements)) {
        EmptyRepro()
        return
    }
    var optimakina by remember { mutableStateOf(false) }

    // Plan recalculé **uniquement** quand les données réelles changent (succès, stock, toggle) — pas
    // à chaque tick d'horloge du chip réseau. Les StateFlows du sniffer poussant de nouvelles
    // références à chaque état d'enclos, ceci équivaut à un recalcul événementiel (≈ chaque push).
    val remaining = remember(achievements) { ReproTargets.remainingMuldoRobes(achievements) }
    val stock = remember(stable, paddocks, consumed) { OwnedStock.from(stable, paddocks.values, consumed) }
    // Éléments actifs par enclos (clé = Paddock.id) : le « en cours » se juge enclos par enclos, donc
    // changer d'onglet d'enclos in-game ne réordonne plus les étapes.
    val activeByPaddock = remember(paddocks) { paddocks.values.associate { it.id to it.activeElements.toSet() } }
    val plan by rememberReproPlan(remaining, stock, optimakina, activeByPaddock)
    val total = MuldoRobes.ALL.size
    // Nombre d'enclos débloqués : `unlockedPaddocks` (poussé par `huf` à l'ouverture de l'écran
    // d'élevage) est la source fiable. Tant que ce message n'a pas été vu, on retombe sur le nombre
    // d'enclos déjà ouverts par le joueur (sous-estimation possible, mais jamais 0 s'il a déjà ouvert
    // un enclos) — purement indicatif pour le chip d'en-tête, aucune logique de plan n'en dépend.
    val nbEnclos = remember(unlockedPaddocks, paddocks) {
        unlockedPaddocks.count { it.value }.takeIf { unlockedPaddocks.isNotEmpty() } ?: paddocks.keys.count { it != null }
    }

    val clipboard = LocalClipboardManager.current
    var showCalc by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            ReproHeader(
                remaining.size, total, nbEnclos, now, lastGameFrameAt,
                onCopyState = { clipboard.setText(AnnotatedString(ReproDump.build(stable, paddocks, plan, remaining))) },
                onShowCalc = { showCalc = true },
            )
            Spacer(Modifier.height(12.dp))
            SpeciesTabs()
            Spacer(Modifier.height(10.dp))
            OptimakinaToggle(optimakina, onChange = { optimakina = it })
            Spacer(Modifier.height(12.dp))

            if (plan.fullSuccess) {
                FullSuccess()
            } else {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Checklist(plan)
                    Spacer(Modifier.height(16.dp))
                    RemainingRobes(remaining)
                }
            }
        }
        if (showCalc) ReproCalcOverlay(plan, optimakina, onClose = { showCalc = false })
    }
}

/**
 * Calcule le plan **hors du thread de composition** (`Dispatchers.Default`) plutôt qu'en ligne dans
 * un `remember` : `ReproPlanner.plan` reste rapide aujourd'hui (arbre de 120 robes), mais
 * [produceState] relance et **annule automatiquement** le calcul en cours dès qu'un paramètre change
 * (nouvel état sniffer), ce qui évite qu'un calcul obsolète écrase un résultat plus récent en cas de
 * recompositions rapprochées, et laisse de la marge si le planificateur se complexifie (recherche
 * beam, ordonnancement de fournées…).
 */
@Composable
private fun rememberReproPlan(
    remaining: List<String>,
    stock: OwnedStock,
    optimakina: Boolean,
    activeByPaddock: Map<Int?, Set<Int>>,
): State<ReproPlan> = produceState(
    initialValue = ReproPlan(emptyList(), emptyList(), remaining, fullSuccess = remaining.isEmpty()),
    remaining, stock, optimakina, activeByPaddock,
) {
    value = withContext(Dispatchers.Default) { ReproPlanner.plan(remaining, stock, optimakina, activeByPaddock) }
}

/* ------------------------------------------------------------ Checklist (groupée par jauge) */

/** Une montée de jauge à faire : la [mount], la [gauge] à monter (valeur/max), et son statut « en cours ». */
private data class GaugeDirective(val mount: OwnedMount, val gauge: MountGauge, val inProgress: Boolean)

/** Un réglage de sérénité à faire : la [mount] et les jauges qu'on **débloquera** une fois la sérénité ajustée. */
private data class SerenityDirective(val mount: OwnedMount, val unlocks: List<MountGauge>, val inProgress: Boolean)

/** Ordre d'affichage des groupes de jauges (amour, maturité, endurance). */
private val GAUGE_DISPLAY_ORDER = listOf(MountGauge.TYPE_LOVE, MountGauge.TYPE_MATURITY, MountGauge.TYPE_ENDURANCE)

/**
 * Checklist du coach, pensée pour le workflow in-game (**1 enclos par type de jauge**) : les croisements
 * prêts en tête, puis les montées **regroupées par type de jauge** (un bloc Amour, un bloc Maturité…),
 * un bloc « ajuster la sérénité », et enfin les autres préparations. Dans chaque bloc, les montures de
 * **plus haute génération** sont en tête (on converge vers les vraies cibles). L'état live fait avancer
 * chaque étape, sans action manuelle.
 */
@Composable
private fun Checklist(plan: ReproPlan) {
    val ready = plan.stepsOf(StepStatus.READY)

    // Décompose chaque montée de jauge en directives **par type** : une monture qui doit monter amour
    // ET endurance apparaît dans les deux blocs (c'est exactement le travail enclos par enclos). Si
    // toutes ses jauges manquantes sont bloquées par la sérénité → bloc « ajuster la sérénité ».
    val byGauge = LinkedHashMap<Int, MutableList<GaugeDirective>>()
    val serenity = mutableListOf<SerenityDirective>()
    plan.steps.forEach { step ->
        val a = step.action
        if (a is NextAction.RaiseGauges) {
            val m = a.mount
            val raisable = m.gaugesRaisableNow()
            val inProgress = step.status == StepStatus.IN_PROGRESS
            if (raisable.isNotEmpty()) {
                raisable.forEach { g -> byGauge.getOrPut(g.type) { mutableListOf() } += GaugeDirective(m, g, inProgress) }
            } else {
                serenity += SerenityDirective(m, m.gaugesMissing(), inProgress)
            }
        }
    }
    // Autres préparations : clones, captures, croisements intermédiaires. On masque NeedOppositeSex :
    // le standby « pas de sexe opposé » n'est pas une action et ne fait que polluer la checklist.
    val otherPrep = plan.stepsOf(StepStatus.TO_PREPARE)
        .filter { it.action !is NextAction.RaiseGauges && it.action !is NextAction.NeedOppositeSex }

    var first = true
    @Composable fun gap() { if (!first) Spacer(Modifier.height(16.dp)); first = false }

    if (ready.isNotEmpty()) {
        gap()
        StepSection("🏆 Croiser — valide un succès", ready.size, BreedColors.feconde) {
            ready.forEach { StepRow(it.action) }
        }
    }
    GAUGE_DISPLAY_ORDER.forEach { type ->
        val directives = byGauge[type]?.sortedWith(byGenDescThenLevel { it.mount }) ?: return@forEach
        gap()
        GaugeSection(type, directives)
    }
    // Sérénité scindée en 2 blocs : monter (bandes RED/BLUE, débloque maturité/amour) vs baisser
    // (bandes PURPLE/GREEN, débloque endurance/maturité) — un enclos « caresseur » et un « baffeur ».
    val (toRaise, toLower) = serenity.partition { needsSerenityRaise(it.mount) }
    if (toRaise.isNotEmpty()) {
        gap()
        SerenitySection("Monter la sérénité", AppIcons.serenityUp, BreedColors.serenityPurple, toRaise.sortedWith(byGenDescThenLevel { it.mount }))
    }
    if (toLower.isNotEmpty()) {
        gap()
        SerenitySection("Baisser la sérénité", AppIcons.serenityDown, BreedColors.serenityRed, toLower.sortedWith(byGenDescThenLevel { it.mount }))
    }
    if (plan.justBred.isNotEmpty()) {
        gap()
        StepSection("⏳ Vient d'être accouplée", plan.justBred.size, BreedColors.gold) {
            plan.justBred.forEach { JustBredRow(it) }
        }
    }
    if (otherPrep.isNotEmpty()) {
        gap()
        StepSection("🧬 Produire les parents manquants", otherPrep.size, MaterialTheme.colorScheme.primary) {
            otherPrep.forEach { StepRow(it.action) }
        }
    }
}

/** Comparateur : génération **décroissante** (plus haute en tête) puis niveau décroissant. */
private fun <T> byGenDescThenLevel(mountOf: (T) -> OwnedMount): Comparator<T> =
    compareByDescending<T> { MuldoRobes.byName(mountOf(it).robe)?.gen ?: 0 }
        .thenByDescending { mountOf(it).level }

/** Bloc « Monter <jauge> » : icône + montures concernées (vignettes qui s'enroulent), plus haute gen en tête. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GaugeSection(type: Int, directives: List<GaugeDirective>) {
    val accent = gaugeColor(type)
    SectionShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(AppIcons.gauge(type)), null, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("Monter ${gaugeName(type)} (${directives.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            directives.forEach { d ->
                Row(Modifier.alpha(if (d.inProgress) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically) {
                    MountRef(d.mount)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${shortK(d.gauge.value)}/${shortK(Fertility.GAUGE_MAX)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    if (d.inProgress) { Spacer(Modifier.width(4.dp)); Text("⏳", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

/**
 * Une monture en standby sérénité doit-elle la **monter** (bandes RED/BLUE → débloque maturité/amour)
 * ou la **baisser** (bandes PURPLE/GREEN → débloque endurance/maturité) ? La bande courante suffit à
 * trancher : les jauges qu'il lui reste à monter sont toujours du même côté.
 */
private fun needsSerenityRaise(m: OwnedMount): Boolean =
    m.serenityBand == SerenityBand.RED || m.serenityBand == SerenityBand.BLUE

/** Bloc « Monter / Baisser la sérénité » : icône + montures, avec les jauges qu'on débloquera. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SerenitySection(title: String, icon: DrawableResource, accent: Color, directives: List<SerenityDirective>) {
    SectionShell(accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(18.dp), tint = accent)
            Spacer(Modifier.width(8.dp))
            Text("$title (${directives.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            directives.forEach { d ->
                Row(Modifier.alpha(if (d.inProgress) 0.55f else 1f), verticalAlignment = Alignment.CenterVertically) {
                    MountRef(d.mount)
                    Spacer(Modifier.width(5.dp))
                    Text("→", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    d.unlocks.forEach { g ->
                        Spacer(Modifier.width(3.dp))
                        Icon(painterResource(AppIcons.gauge(g.type)), gaugeName(g.type), Modifier.size(14.dp), tint = gaugeColor(g.type))
                    }
                    if (d.inProgress) { Spacer(Modifier.width(4.dp)); Text("⏳", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

/** Coque commune d'un bloc de la checklist (surface arrondie + liseré d'accent). */
@Composable
private fun SectionShell(accent: Color, body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) { body() }
}

@Composable
private fun StepSection(title: String, count: Int, accent: Color, body: @Composable () -> Unit) {
    SectionShell(accent) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Spacer(Modifier.height(8.dp))
        body()
    }
}

/** Une ligne de checklist : rend l'action selon son type. */
@Composable
private fun StepRow(action: NextAction) {
    Box(Modifier.padding(vertical = 4.dp)) { ActionBody(action) }
}

/** Monture juste accouplée : feedback « en cours, jauges en reset ». */
@Composable
private fun JustBredRow(m: OwnedMount) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MountRef(m)
        Spacer(Modifier.width(8.dp))
        Text(
            "vient d'être accouplée — jauges en reset",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReproHeader(
    remaining: Int, total: Int, nbEnclos: Int, now: Long, lastGameFrameAt: Long?,
    onCopyState: () -> Unit, onShowCalc: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Reproduction", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        CopyButton("📋", "Copier l'état", onCopyState)
        Spacer(Modifier.width(6.dp))
        HeaderChip("🧮", "Récap calcul", onShowCalc)
        Spacer(Modifier.weight(1f))
        NetworkChip(now, lastGameFrameAt)
        Spacer(Modifier.width(12.dp))
        if (nbEnclos > 0) {
            Text(
                "🚧 $nbEnclos enclos (${nbEnclos * 10} places)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            "$remaining robes restantes / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Petit bouton discret « copier vers le presse-papier » (debug) : [icon] + [label], feedback « Copié ». */
@Composable
private fun CopyButton(icon: String, label: String, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onCopy(); copied = true }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (copied) "✓" else icon, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(4.dp))
        Text(
            if (copied) "Copié" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Petit bouton d'en-tête (icône + label) déclenchant une action ponctuelle (ex. ouvrir le récap). */
@Composable
private fun HeaderChip(icon: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ------------------------------------------------------------ Récap calcul des bébés (popup) */

/**
 * Overlay fermable expliquant **comment sont calculés les bébés obtenables** (modèle généalogie 2026,
 * cf. [BreedingGenetics]) pour chaque croisement recommandé par le plan : arbres pondérés des parents,
 * génération cible, et la distribution des robes possibles avec leurs probabilités. Sert à vérifier le
 * modèle contre les probabilités affichées en jeu. Scrim cliquable + croix pour fermer.
 */
@Composable
private fun ReproCalcOverlay(plan: ReproPlan, optimakina: Boolean, onClose: () -> Unit) {
    val crosses = remember(plan) { plan.steps.mapNotNull { it.action as? NextAction.Cross } }
    Box(
        Modifier.fillMaxSize().background(Color(0xE60B0E13)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // clickable no-op : absorbe le clic pour ne pas fermer quand on interagit avec la carte.
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.9f).clickable(onClick = {}),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Comment les bébés sont calculés", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(CircleShape).clickable(onClick = onClose).padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Modèle 2026 : un croisement entre une race de l'arbre du père et une de l'arbre de la mère " +
                        "(parents ×5, grands-parents ×3 · poids mono 9, bi 2, mono gen 9 = 2). La génération la plus " +
                        "haute atteignable (« cible ») reçoit 30 %+0,15 %/niv" +
                        (if (optimakina) " +10 % optimakina" else "") + ", réparti au prorata des poids ; le reste va aux autres générations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))

                if (crosses.isEmpty()) {
                    Text(
                        "Aucun croisement concret proposé pour l'instant.\nReviens quand le coach affiche une étape « Croiser — valide un succès » ou un croisement intermédiaire.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        crosses.forEachIndexed { i, c ->
                            if (i > 0) Spacer(Modifier.height(16.dp))
                            CrossCalcCard(c, optimakina)
                        }
                    }
                }
            }
        }
    }
}

/** Détail du calcul d'un croisement : parents + arbres pondérés, génération cible, robes possibles. */
@Composable
private fun CrossCalcCard(c: NextAction.Cross, optimakina: Boolean) {
    val out = remember(c, optimakina) {
        BreedingGenetics.compute(
            fatherRobe = c.father.robe, fatherParents = c.father.parents, fatherLevel = c.father.level,
            motherRobe = c.mother.robe, motherParents = c.mother.parents, motherLevel = c.mother.level,
            optimakina = optimakina,
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)).padding(12.dp),
    ) {
        // Titre : robe visée + parents.
        Row(verticalAlignment = Alignment.CenterVertically) {
            RobeMark(c.target, 22.dp)
            Spacer(Modifier.width(8.dp))
            Text("vise ${c.target}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text("G${c.targetGen}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CrossFinality(c)
        Spacer(Modifier.height(6.dp))
        TreeLine("♀", c.mother.robe, c.mother.level, c.mother.parents)
        TreeLine("♂", c.father.robe, c.father.level, c.father.parents)
        Spacer(Modifier.height(8.dp))

        // Génération cible + note sur la robe visée.
        val visee = out.chances.firstOrNull { it.robe == c.target }
        val note = when {
            visee == null -> "robe visée absente du pool — ce couple ne peut pas la produire"
            visee.targetGen -> "robe visée = génération cible"
            else -> "⚠ la robe visée n'est pas la génération cible (le pool atteint plus haut)"
        }
        Text(
            "Génération cible G${out.targetGen} · ${pctStr(out.pTargetGen)} — $note",
            style = MaterialTheme.typography.labelMedium,
            color = if (visee?.targetGen == true) BreedColors.feconde else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // Robes possibles, proba décroissante.
        out.chances.forEach { rc ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pctStr(rc.p), style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (rc.targetGen) FontWeight.Bold else FontWeight.Normal,
                    color = if (rc.robe == c.target) BreedColors.feconde else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(64.dp),
                )
                RobeMark(rc.robe, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(rc.robe, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Text("G${rc.gen}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rc.robe == c.target) {
                    Spacer(Modifier.width(6.dp))
                    Text("◀ visée", style = MaterialTheme.typography.labelSmall, color = BreedColors.feconde)
                }
            }
        }
    }
}

/** Une ligne d'arbre d'un parent : sexe, robe propre (×5), grands-parents (×3). */
@Composable
private fun TreeLine(sex: String, robe: String, level: Int, parents: List<String>) {
    val gp = if (parents.isEmpty()) "grands-parents inconnus"
    else parents.joinToString(", ") { "$it ×3" }
    Text(
        "$sex $robe niv$level ×5  ·  $gp",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Justifie un croisement par sa **finalité** : soit il valide directement le succès visé, soit c'est un
 * **maillon intermédiaire** vers un (ou plusieurs) succès restant plus haut. Rien si non calculé.
 */
@Composable
private fun CrossFinality(action: NextAction.Cross) {
    if (action.finalTargets.isEmpty()) return
    val downstream = action.finalTargets.filter { it != action.target }
    Row(
        Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (downstream.isEmpty()) {
            Text(
                "🎯 objectif final — ce croisement valide le succès visé",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val goal = downstream.first()
            Text(
                "↳ maillon vers l'objectif ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RobeMark(goal, 14.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                "$goal G${MuldoRobes.byName(goal)?.gen ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (downstream.size > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "+${downstream.size - 1} autre(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun pctStr(p: Double): String = "%.2f%%".format(p * 100)

/** Sélecteur d'espèce : Muldo actif, les autres « bientôt » (grisées). */
@Composable
private fun SpeciesTabs() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeciesChip("Muldo", active = true, enabled = true)
        SpeciesChip("Dragodinde", active = false, enabled = false)
        SpeciesChip("Volkorne", active = false, enabled = false)
    }
}

@Composable
private fun SpeciesChip(label: String, active: Boolean, enabled: Boolean) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val fg = when {
        active -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, color = fg)
        if (!enabled) {
            Spacer(Modifier.width(6.dp))
            Text("bientôt", style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

@Composable
private fun OptimakinaToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Utiliser une Optimakina (+10 % de réussite par croisement)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onChange(!checked) },
        )
    }
}

/* ----------------------------------------------------------------- Rendu d'une action */

@Composable
private fun ActionBody(action: NextAction) {
    when (action) {
        is NextAction.Cross -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Croise ", style = MaterialTheme.typography.bodyMedium)
                MountRef(action.mother)
                Text("  avec  ", style = MaterialTheme.typography.bodyMedium)
                MountRef(action.father)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("→ vise ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RobeChip(action.target, action.targetGen)
                Spacer(Modifier.width(10.dp))
                Text(
                    "robe visée ≈ ${pct(action.pTargetRobe)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = successColor(action.pTargetRobe),
                )
            }
            CrossFinality(action)
        }

        is NextAction.RaiseGauges -> Row(verticalAlignment = Alignment.CenterVertically) {
            val m = action.mount
            val raisable = m.gaugesRaisableNow()
            if (raisable.isNotEmpty()) {
                Text("Monte ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    raisable.joinToString(" + ") { "${gaugeName(it.type)} ${shortK(it.value)}/${shortK(Fertility.GAUGE_MAX)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(" de ", style = MaterialTheme.typography.bodyMedium)
                MountRef(m)
            } else {
                // Les jauges manquantes sont bloquées par la sérénité actuelle → l'ajuster d'abord.
                Text("Ajuste la sérénité de ", style = MaterialTheme.typography.bodyMedium)
                MountRef(m)
                Spacer(Modifier.width(6.dp))
                Text(
                    "pour monter ${m.gaugesMissing().joinToString(" + ") { gaugeName(it.type) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NextAction.Clone -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clone tes 2 ", style = MaterialTheme.typography.bodyMedium)
            RobeChip(action.robe, MuldoRobes.byName(action.robe)?.gen)
            Text(" stériles → 1 féconde", style = MaterialTheme.typography.bodyMedium)
        }

        is NextAction.Capture -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Capture une Muldo ", style = MaterialTheme.typography.bodyMedium)
            RobeChip(action.robe, 1)
            Text("  (gen 1, seules capturables)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is NextAction.NeedOppositeSex -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RobeChip(action.parentA, MuldoRobes.byName(action.parentA)?.gen)
                Text("  et  ", style = MaterialTheme.typography.bodyMedium)
                RobeChip(action.parentB, MuldoRobes.byName(action.parentB)?.gen)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tes fécondes sont toutes du même sexe (${sexLabel(action.have)}) : il te faut un mâle ET une femelle pour viser ${action.target}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Référence d'une monture du stock : sexe + nom + vignette de robe + niveau + sérénité + localisation. */
@Composable
private fun MountRef(m: OwnedMount) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (m.sex == Sex.MALE) "♂" else "♀", color = sexColor(m.sex), fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(5.dp))
        Text(
            if (!m.name.isNullOrBlank()) m.name else "Anonyme",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        RobeMark(m.robe, 18.dp)
        Spacer(Modifier.width(4.dp))
        Text(m.robe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Text("niv ${m.level}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        // Sérénité (signée + smiley de bande) : aide à retrouver la monture (l'étable se trie par sérénité).
        SerenitySmiley(m.serenityBand, diameter = 13.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            signedSerenity(m.serenity),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = serenityColor(m.serenityBand),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "· ${locationLabel(m.location)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = locationColor(m.location),
            maxLines = 1,
        )
    }
}

/** Libellé court de localisation d'une monture (où aller la chercher). */
private fun locationLabel(loc: MountLocation): String = when (loc) {
    is MountLocation.Stable -> "Étable"
    is MountLocation.Paddock -> loc.id?.let { "Enclos $it" } ?: "Enclos actif"
}

@Composable
private fun locationColor(loc: MountLocation): androidx.compose.ui.graphics.Color = when (loc) {
    is MountLocation.Stable -> MaterialTheme.colorScheme.onSurfaceVariant
    is MountLocation.Paddock -> BreedColors.fence
}

/** Pastille de robe + nom (+ génération optionnelle). */
@Composable
private fun RobeChip(robe: String, gen: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RobeMark(robe, 20.dp)
        Spacer(Modifier.width(5.dp))
        Text(robe, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (gen != null) {
            Spacer(Modifier.width(4.dp))
            Text("G$gen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ------------------------------------------------------------------ Robes restantes */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemainingRobes(remaining: List<String>) {
    if (remaining.isEmpty()) return
    Text("Robes restantes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    val byGen = remaining.groupBy { MuldoRobes.byName(it)?.gen ?: 0 }.toSortedMap()
    byGen.forEach { (gen, robes) ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("Gen $gen", Modifier.width(54.dp).padding(top = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                robes.forEach { robe ->
                    Row(
                        Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RobeMark(robe, 18.dp)
                        Spacer(Modifier.width(5.dp))
                        Text(robe, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- États / utils */

@Composable
private fun EmptyRepro() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "En attente des succès Muldo…\nOuvre tes succès d'élevage Muldo dans le jeu pour que le planificateur sache quelles robes te manquent.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FullSuccess() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "🏆 Full succès Muldo atteint !\nToutes les robes connues ont été validées.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BreedColors.feconde,
        )
    }
}

private fun pct(p: Double): String = "${(p * 100).toInt()} %"

@Composable
private fun successColor(p: Double) = when {
    p >= 0.80 -> BreedColors.feconde
    p >= 0.50 -> BreedColors.gold
    else -> BreedColors.serenityRed
}

private fun sexLabel(s: Sex): String = if (s == Sex.MALE) "mâle" else "femelle"

private fun signedSerenity(s: Int): String = if (s > 0) "+$s" else s.toString()

private fun gaugeName(type: Int): String = when (type) {
    MountGauge.TYPE_LOVE -> "amour"
    MountGauge.TYPE_MATURITY -> "maturité"
    else -> "endurance"
}
