package it.vittorioscocca.kidbox.ui.components

import android.content.Context
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentFocus
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

/** Le due radici della barra. L'assistente non è una radice: si apre sopra. */
enum class BottomBarTab { HOME, NEWS }

private val AiOrange = Color(0xFFFF6B00)
private val AiOrangeDeep = Color(0xFFEB5205)

/**
 * La barra in basso: Home · assistente AI · Notizie (dal 03/10/2026, al posto
 * dei pulsanti AI di Home e Salute). Ha lo stesso fondo della striscia sotto
 * la barra di navigazione di sistema (`kidBoxColors.background`, dipinto dalla
 * radice in MainActivity), così le due sembrano una superficie sola. Il cerchio
 * al centro, più grande delle altre voci, non è una scheda: apre l'assistente.
 * Gemella di `KBLiquidTabBar.swift`, che su iOS è in vetro liquido.
 *
 * Scorrendo per leggere oltre si fa compatta (solo icone, cerchio più piccolo)
 * e lascia lo spazio al contenuto; torna grande scorrendo indietro o cambiando
 * schermata ([BottomBarScrollState]). Il cerchio sta dentro la barra in
 * entrambe le misure: fino al 03/10/2026 sporgeva di 12 dp sopra il bordo e
 * copriva l'ultima riga o il pulsante in fondo alla schermata.
 */
@Composable
fun KidBoxBottomBar(
    selected: BottomBarTab,
    onHome: () -> Unit,
    onAssistant: () -> Unit,
    onNews: () -> Unit,
    scroll: BottomBarScrollState? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    // Letto qui e non da chi chiama: a ogni cambio si ricompone solo la barra.
    val minimized = scroll?.isMinimized ?: false
    val motion = spring<Dp>(dampingRatio = 0.82f, stiffness = 500f)
    val barHeight by animateDpAsState(if (minimized) 52.dp else 72.dp, motion, label = "barHeight")
    val circle by animateDpAsState(if (minimized) 42.dp else 60.dp, motion, label = "assistantSize")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kb.background),
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = kb.divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(
                label = stringResource(R.string.bottom_bar_home),
                icon = Icons.Outlined.Home,
                selectedIcon = Icons.Filled.Home,
                isSelected = selected == BottomBarTab.HOME,
                minimized = minimized,
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(96.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                AssistantButton(size = circle, onClick = onAssistant)
            }
            BarItem(
                label = stringResource(R.string.bottom_bar_news),
                icon = Icons.Outlined.Newspaper,
                selectedIcon = Icons.Filled.Newspaper,
                isSelected = selected == BottomBarTab.NEWS,
                minimized = minimized,
                onClick = onNews,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Rimpicciolisce la barra quando si scorre per leggere oltre (dito verso
 * l'alto) e la riallarga tornando indietro: gemello di
 * `KBTabBarScrollObserver.swift`. Sta sul Box del NavHost, quindi sente le
 * liste e le colonne che scorrono di tutte le schermate con la barra, senza
 * toccarle. Conta solo lo scorrimento verticale davvero consumato: una pagina
 * corta che non scorre e un carosello orizzontale non rimpiccioliscono niente.
 */
@Stable
class BottomBarScrollState(private val thresholdPx: Float) : NestedScrollConnection {

    var isMinimized by mutableStateOf(false)
        private set

    /** Spostamento nella stessa direzione: sotto la soglia un tremolio del dito farebbe pulsare la barra. */
    private var travel = 0f

    fun reset() {
        isMinimized = false
        travel = 0f
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val dy = consumed.y
        if (dy != 0f) {
            if ((dy < 0f) != (travel < 0f)) travel = 0f
            travel += dy
            if (travel < -thresholdPx) {
                isMinimized = true
                travel = 0f
            } else if (travel > thresholdPx) {
                isMinimized = false
                travel = 0f
            }
        } else if (available.y > 0f) {
            // In cima e si tira ancora giù: grande.
            reset()
        }
        return Offset.Zero
    }
}

@Composable
fun rememberBottomBarScrollState(): BottomBarScrollState {
    val thresholdPx = with(LocalDensity.current) { 28.dp.toPx() }
    return remember(thresholdPx) { BottomBarScrollState(thresholdPx) }
}

@Composable
private fun BarItem(
    label: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    minimized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val tint = if (isSelected) AiOrange else kb.subtitle
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 36.dp),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                selected = isSelected
                // Compatta l'etichetta non c'è: il nome lo dà la descrizione.
                if (minimized) contentDescription = label
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isSelected) AiOrange.copy(alpha = 0.14f) else Color.Transparent)
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Icon(if (isSelected) selectedIcon else icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        AnimatedVisibility(
            visible = !minimized,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                text = label,
                color = tint,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AssistantButton(size: Dp, onClick: () -> Unit) {
    val description = stringResource(R.string.bottom_bar_assistant)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Più grande delle altre voci, ma dentro la barra: non copre il contenuto sopra.
            .size(size)
            .shadow(8.dp, CircleShape, clip = false, ambientColor = AiOrange, spotColor = AiOrange)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AiOrange, AiOrangeDeep)))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}

// ── Dove compare e con quale focus ──────────────────────────────────────────

/** Le schermate con la barra: le due radici e Salute, dove prima c'era il pulsante AI. */
object BottomBarRoutes {
    private val healthRoutes = setOf(
        AppDestination.PediatricChildSelector.route,
        AppDestination.HealthHome.route,
        AppDestination.MedicalRecord.route,
        AppDestination.ClinicalRecord.route,
        AppDestination.HealthConnectApp.route,
        AppDestination.MedicalVisits.route,
        AppDestination.MedicalVisitDetail.route,
        AppDestination.Vaccines.route,
        AppDestination.MedicalExams.route,
        AppDestination.MedicalExamDetail.route,
        AppDestination.Treatments.route,
        AppDestination.TreatmentDetail.route,
        AppDestination.HealthTimeline.route,
    )

    fun showsBar(route: String?): Boolean =
        route == AppDestination.Home.route || route == AppDestination.News.route || route in healthRoutes

    fun tabFor(route: String?): BottomBarTab = if (route == AppDestination.News.route) BottomBarTab.NEWS else BottomBarTab.HOME
}

/**
 * Il focus dell'assistente aperto dalla barra: da una schermata di Salute lo
 * centra su quella persona, visita o esame, come facevano i pulsanti che ha
 * sostituito. Il nome viene da Room (figlio o adulto, come in Salute).
 */
@HiltViewModel
class BottomBarViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val childDao: KBChildDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
) : ViewModel() {

    suspend fun focusFor(route: String?, args: Bundle?): AgentFocus? {
        val familyId = args?.getString("familyId").orEmpty()
        val childId = args?.getString("childId").orEmpty()
        if (childId.isBlank()) return null
        val name = personName(familyId, childId) ?: return null
        return when (route) {
            AppDestination.HealthHome.route,
            AppDestination.MedicalRecord.route,
            AppDestination.ClinicalRecord.route,
            AppDestination.HealthConnectApp.route,
            AppDestination.Vaccines.route,
            AppDestination.Treatments.route,
            AppDestination.TreatmentDetail.route,
            AppDestination.HealthTimeline.route,
            -> AgentFocus(personId = childId, personName = name, scope = AgentFocus.Scope.PERSON)
            AppDestination.MedicalVisits.route ->
                AgentFocus(personId = childId, personName = name, scope = AgentFocus.Scope.VISITS)
            AppDestination.MedicalVisitDetail.route -> {
                val visitId = args?.getString("visitId").orEmpty()
                val visit = visitDao.getById(visitId)
                val detail = visit?.let {
                    context.getString(
                        R.string.agent_focus_visit_of_date,
                        SimpleDateFormat("d MMM yyyy", KBLocale.current()).format(Date(it.dateEpochMillis)),
                    )
                }
                AgentFocus(personId = childId, personName = name, scope = AgentFocus.Scope.VISIT, itemId = visitId, detail = detail)
            }
            AppDestination.MedicalExams.route ->
                AgentFocus(personId = childId, personName = name, scope = AgentFocus.Scope.EXAMS)
            AppDestination.MedicalExamDetail.route -> {
                val examId = args?.getString("examId").orEmpty()
                val exam = examDao.getById(examId)
                AgentFocus(personId = childId, personName = name, scope = AgentFocus.Scope.EXAM, itemId = examId, detail = exam?.name?.trim())
            }
            else -> null
        }
    }

    private suspend fun personName(familyId: String, personId: String): String? {
        childDao.getById(personId)?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return familyMemberDao.getActiveByFamilyAndUser(familyId, personId)?.displayName?.takeIf { it.isNotBlank() }
    }
}
