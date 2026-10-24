package it.vittorioscocca.kidbox.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.HomeViewModePreference
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

private val Accent = Color(0xFFFF6B00)

/**
 * I widget della Home (Dashboard e Meteo): Impostazioni > Widget, il foglio
 * che si apre dal consiglio in Home e il consiglio stesso. Le regole su quando
 * il consiglio compare stanno in [HomeViewModePreference.isWidgetHintAllowed].
 */
@HiltViewModel
class WidgetsViewModel @Inject constructor(
    private val homeViewModePreference: HomeViewModePreference,
) : ViewModel() {
    val showDashboard: StateFlow<Boolean> = homeViewModePreference.getShowDashboardFlow()
    val showWeather: StateFlow<Boolean> = homeViewModePreference.getShowWeatherFlow()

    fun setShowDashboard(show: Boolean) = homeViewModePreference.setShowDashboard(show)
    fun setShowWeather(show: Boolean) = homeViewModePreference.setShowWeather(show)

    fun isHintAllowed(): Boolean = homeViewModePreference.isWidgetHintAllowed()
    fun neverShowHint() = homeViewModePreference.neverShowWidgetHint()
}

/**
 * Accende il meteo e, se manca, chiede la posizione approssimata: senza, la
 * barra non avrebbe niente da mostrare.
 */
@Composable
private fun rememberWeatherEnabler(viewModel: WidgetsViewModel): (Boolean) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { on ->
        viewModel.setShowWeather(on)
        if (on && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

/** Riga riassuntiva per l'elenco delle Impostazioni. */
@Composable
fun widgetSummary(dashboard: Boolean, weather: Boolean): String {
    val parts = buildList {
        if (dashboard) add(stringResource(R.string.settings_widget_dashboard))
        if (weather) add(stringResource(R.string.settings_widget_weather))
    }
    return if (parts.isEmpty()) stringResource(R.string.settings_widget_none) else parts.joinToString(", ")
}

// ── Impostazioni > Widget ────────────────────────────────────────────────────

@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    viewModel: WidgetsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val showDashboard by viewModel.showDashboard.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    val setWeather = rememberWeatherEnabler(viewModel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_row_widgets),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        ) {
            WidgetToggleRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_home_show_dashboard),
                checked = showDashboard,
                onChange = { viewModel.setShowDashboard(it) },
            )
            Divider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.kidBoxColors.divider,
            )
            WidgetToggleRow(
                icon = Icons.Filled.Cloud,
                title = stringResource(R.string.settings_home_show_weather),
                checked = showWeather,
                onChange = setWeather,
            )
        }
        Text(
            text = stringResource(R.string.settings_widget_footer),
            fontSize = 13.sp,
            color = MaterialTheme.kidBoxColors.subtitle,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
    }
}

@Composable
private fun WidgetToggleRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(12.dp))
            Text(title, fontSize = 16.sp, color = MaterialTheme.kidBoxColors.title)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Accent),
        )
    }
}

// ── Consiglio in Home e foglio ───────────────────────────────────────────────

/**
 * Pillola sotto la foto di famiglia: bordo tratteggiato che scorre e freccia
 * che rimbalza. Non disegna niente se un widget è già acceso o se il consiglio
 * non tocca a questo avvio.
 */
@Composable
fun HomeWidgetHint(
    modifier: Modifier = Modifier,
    viewModel: WidgetsViewModel = hiltViewModel(),
) {
    val showDashboard by viewModel.showDashboard.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    if (sheetOpen) {
        HomeWidgetPickerSheet(viewModel = viewModel, onDismiss = { sheetOpen = false })
    }
    if (showDashboard || showWeather || !viewModel.isHintAllowed()) return

    val transition = rememberInfiniteTransition(label = "widgetHint")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dash",
    )
    val bounce by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "bounce",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(color = Accent.copy(alpha = 0.08f), cornerRadius = r)
                drawRoundRect(
                    color = Accent,
                    cornerRadius = r,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), -phase.dp.toPx()),
                    ),
                )
            }
            .clickable { sheetOpen = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(22.dp).offset(y = bounce.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            stringResource(R.string.home_widget_hint),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.Add, contentDescription = null, tint = Accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeWidgetPickerSheet(viewModel: WidgetsViewModel, onDismiss: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val showDashboard by viewModel.showDashboard.collectAsStateWithLifecycle()
    val showWeather by viewModel.showWeather.collectAsStateWithLifecycle()
    val setWeather = rememberWeatherEnabler(viewModel)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = kb.background,
        contentColor = kb.title,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.home_widget_hint),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
            ) {
                WidgetOption(
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.settings_widget_weather),
                    subtitle = stringResource(R.string.home_widget_weather_desc),
                    added = showWeather,
                    onAdd = { setWeather(true) },
                )
                Divider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = kb.divider)
                WidgetOption(
                    icon = Icons.Filled.GridView,
                    title = stringResource(R.string.settings_widget_dashboard),
                    subtitle = stringResource(R.string.home_widget_dashboard_desc),
                    added = showDashboard,
                    onAdd = { viewModel.setShowDashboard(true) },
                )
            }
            Text(
                stringResource(R.string.home_widget_settings_note),
                fontSize = 13.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = {
                    viewModel.neverShowHint()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_widget_never), color = Color(0xFFE53E3E))
            }
        }
    }
}

@Composable
private fun WidgetOption(icon: ImageVector, title: String, subtitle: String, added: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
        }
        Spacer(modifier = Modifier.size(8.dp))
        if (added) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.home_widget_added),
                tint = Color(0xFF34C759),
                modifier = Modifier.size(28.dp),
            )
        } else {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) {
                Text(stringResource(R.string.home_widget_add))
            }
        }
    }
}
