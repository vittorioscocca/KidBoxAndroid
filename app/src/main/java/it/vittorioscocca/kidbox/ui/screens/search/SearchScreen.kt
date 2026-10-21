package it.vittorioscocca.kidbox.ui.screens.search

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentLinks
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentMemoryRepository
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Cerca in KidBox» dalla Home: risultati per sezione mentre si scrive, un
 * tocco apre la voce sopra la ricerca (così «Indietro» ci torna), e in fondo
 * «Chiedi all'assistente» con la frase già scritta, per le domande che una
 * ricerca non risolve («quanto abbiamo speso di scuola?»). Come `KBSearchView` su iOS.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val agentMemoryRepository: AgentMemoryRepository,
    private val familyDao: KBFamilyDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val familyId: String = savedStateHandle["familyId"] ?: ""

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val index = MutableStateFlow<SearchIndex?>(null)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            // Si legge una volta all'apertura: poi la ricerca è in memoria.
            val name = familyDao.getById(familyId)?.name.orEmpty()
            index.value = SearchIndex(
                agentMemoryRepository.load(familyId, name),
                untitled = context.getString(R.string.search_untitled),
                visit = context.getString(R.string.search_visit),
            )
            _ready.value = true
        }
    }

    /**
     * Un attimo di quiete fra un tasto e l'altro: i testi letti possono
     * essere lunghi e non serve rifare la ricerca a ogni lettera.
     */
    @OptIn(FlowPreview::class)
    val sections: StateFlow<List<SearchIndex.Section>> =
        combine(_query.debounce(200), index) { q, idx -> idx?.search(q.trim()).orEmpty() }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(text: String) = _query.update { text }

    fun routesFor(hit: SearchIndex.Hit): List<String> {
        val idx = index.value ?: return emptyList()
        AppAnalytics.searchResultOpened(context, hit.entry.kind.raw, inText = hit.snippet != null)
        return AgentLinks.destination(hit.entry.item, idx.snapshot).routes
    }

    /** La frase va nel campo dell'assistente, non parte da sola: mandarla costa un messaggio. */
    fun assistantRoute(): String {
        AppAnalytics.searchAskedAssistant(context)
        return AppDestination.AiChat.createRoute(familyId, _query.value.trim())
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val query by viewModel.query.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val trimmed = query.trim()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
            }
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = kb.subtitle) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear), tint = kb.subtitle)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = kb.card,
                    unfocusedContainerColor = kb.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = kb.title,
                    unfocusedTextColor = kb.title,
                ),
            )
        }

        when {
            !ready -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            trimmed.length < 2 -> Text(
                stringResource(R.string.search_hint),
                color = kb.subtitle,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (sections.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_no_results, trimmed),
                            color = kb.subtitle,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                sections.forEach { section ->
                    item(key = "h-${section.kind.raw}") {
                        Text(
                            stringResource(section.kind.searchTitleRes()),
                            color = kb.subtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(section.hits, key = { it.entry.id }) { hit ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .background(kb.card, RoundedCornerShape(10.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    viewModel.routesFor(hit).forEach(onOpenRoute)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(hit.entry.title, color = kb.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (hit.entry.subtitle.isNotBlank()) {
                                Text(hit.entry.subtitle, color = kb.subtitle, fontSize = 12.sp)
                            }
                            hit.snippet?.let {
                                Text(it, color = kb.subtitle, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item(key = "ask") {
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(kb.card, RoundedCornerShape(10.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    onOpenRoute(viewModel.assistantRoute())
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.search_ask_assistant, trimmed), color = kb.title)
                        }
                        Text(
                            stringResource(R.string.search_ask_assistant_footer),
                            color = kb.subtitle,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Titolo della sezione nei risultati della ricerca. */
private fun AgentLinks.Kind.searchTitleRes(): Int = when (this) {
    AgentLinks.Kind.EXPENSE -> R.string.search_section_expenses
    AgentLinks.Kind.DOCUMENT -> R.string.search_section_documents
    AgentLinks.Kind.NOTE -> R.string.search_section_notes
    AgentLinks.Kind.EVENT -> R.string.search_section_calendar
    AgentLinks.Kind.TODO -> R.string.search_section_todo
    AgentLinks.Kind.GROCERY -> R.string.search_section_grocery
    AgentLinks.Kind.VISIT -> R.string.search_section_visits
    AgentLinks.Kind.EXAM -> R.string.search_section_exams
    AgentLinks.Kind.TREATMENT -> R.string.search_section_treatments
    AgentLinks.Kind.TICKET -> R.string.search_section_wallet
    AgentLinks.Kind.HOME -> R.string.search_section_home
    AgentLinks.Kind.VEHICLE -> R.string.search_section_vehicles
    AgentLinks.Kind.PET -> R.string.search_section_pets
    AgentLinks.Kind.TRIP -> R.string.search_section_trips
}
