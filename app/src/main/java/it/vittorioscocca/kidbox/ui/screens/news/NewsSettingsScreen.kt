package it.vittorioscocca.kidbox.ui.screens.news

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao

@HiltViewModel
class NewsSettingsViewModel @Inject constructor(
    val store: NewsPrefsStore,
    val familyStore: NewsFamilyStore,
    val resolver: NewsLocationResolver,
    familyDao: KBFamilyDao,
    familySessionPreferences: FamilySessionPreferences,
) : ViewModel() {
    init {
        // La famiglia attiva, come per la scheda Notizie.
        viewModelScope.launch {
            familyStore.bind(ActiveFamilyResolver.resolveFamilyId(familyDao.getAll(), familySessionPreferences.getActiveFamilyId()))
        }
    }
}

/**
 * Impostazioni → Notizie (e l'icona in alto nella scheda Notizie). Stessi testi
 * di iOS. L'interruttore generale e la città sono della famiglia
 * ([NewsFamilyStore]): cambiano le notizie di tutti i membri. Gli argomenti e
 * le offerte in vista sono di chi legge ([NewsPrefsStore]).
 */
@Composable
fun NewsSettingsScreen(
    onBack: () -> Unit,
    viewModel: NewsSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.store.state.collectAsStateWithLifecycle()
    val familyState by viewModel.familyStore.state.collectAsStateWithLifecycle()
    val family = familyState.settings
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cityQuery by remember { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var placeError by remember { mutableStateOf<String?>(null) }

    fun update(change: (NewsPrefs) -> NewsPrefs) = scope.launch { viewModel.store.update(change) }
    fun updateFamily(change: (NewsFamilySettings) -> NewsFamilySettings) = viewModel.familyStore.update(change)

    fun useLocation() {
        locating = true
        placeError = null
        scope.launch {
            val place = viewModel.resolver.currentPlace()
            locating = false
            if (place == null) placeError = context.getString(R.string.news_place_not_found) else updateFamily { it.copy(place = place) }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useLocation() else placeError = context.getString(R.string.news_place_denied)
    }

    fun searchCity() {
        val query = cityQuery.trim()
        if (query.isEmpty()) return
        locating = true
        placeError = null
        scope.launch {
            val place = viewModel.resolver.place(query)
            locating = false
            if (place == null) {
                placeError = context.getString(R.string.news_place_not_found)
            } else {
                updateFamily { it.copy(place = place) }
                cityQuery = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.news_title), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
        Spacer(Modifier.height(16.dp))

        SettingsCard {
            SwitchRow(
                icon = Icons.Filled.Newspaper,
                tint = Color(0xFFFF6B00),
                title = stringResource(R.string.news_settings_enabled),
                subtitle = stringResource(R.string.news_settings_enabled_sub),
                checked = family.enabled,
                onChange = { on -> updateFamily { it.copy(enabled = on) } },
            )
        }
        Note(stringResource(R.string.news_settings_cost_note))
        Note(stringResource(R.string.news_settings_family_scope))

        SectionHeader(stringResource(R.string.news_settings_topics))
        SettingsCard {
            NewsCategory.entries.forEachIndexed { index, cat ->
                if (index > 0) HorizontalDivider(color = kb.divider)
                val checked = cat in prefs.categories
                SwitchRow(
                    icon = cat.icon,
                    tint = cat.tint,
                    title = stringResource(cat.title),
                    subtitle = stringResource(cat.subtitle),
                    checked = checked,
                    // Almeno un argomento: senza, l'edizione sarebbe vuota.
                    enabled = !(checked && prefs.categories.size == 1),
                    onChange = { on ->
                        update { p ->
                            val set = p.categories.toMutableSet().apply { if (on) add(cat) else remove(cat) }
                            p.copy(categories = NewsCategory.entries.filter { it in set })
                        }
                    },
                )
            }
        }
        Note(stringResource(R.string.news_settings_topics_note))

        SectionHeader(stringResource(R.string.news_settings_place))
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFFFF6B00))
                    Spacer(Modifier.width(10.dp))
                    Text(family.effectivePlace.label, color = kb.title, modifier = Modifier.weight(1f))
                    if (family.place?.hasCity == true) {
                        IconButton(onClick = { updateFamily { p -> p.copy(place = p.place?.copy(city = "", province = "", region = "")) } }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.news_settings_remove_city), tint = kb.subtitle)
                        }
                    }
                }
                TextButton(
                    onClick = {
                        if (hasLocationPermission(context)) useLocation()
                        else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = !locating,
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color(0xFFFF6B00))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.news_settings_use_location), color = Color(0xFFFF6B00))
                    if (locating) {
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = cityQuery,
                        onValueChange = { cityQuery = it },
                        placeholder = { Text(stringResource(R.string.news_settings_city_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchCity() }),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { searchCity() }, enabled = cityQuery.isNotBlank() && !locating) {
                        Text(stringResource(R.string.news_settings_search))
                    }
                }
                placeError?.let { Text(it, color = Color(0xFFE67E22), style = MaterialTheme.typography.bodySmall) }
            }
        }
        Note(stringResource(R.string.news_settings_place_note))

        SectionHeader(stringResource(R.string.news_settings_offers))
        SettingsCard {
            SwitchRow(
                icon = Icons.Filled.Sell,
                tint = NewsCategory.ECONOMY.tint,
                title = stringResource(R.string.news_settings_offers),
                subtitle = stringResource(R.string.news_settings_offers_sub),
                checked = prefs.personalOffers,
                onChange = { on -> update { it.copy(personalOffers = on) } },
            )
        }
        Note(stringResource(R.string.news_settings_offers_note))
        Spacer(Modifier.height(24.dp))
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
private fun SectionHeader(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = kb.subtitle,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = kb.subtitle,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = kb.title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
