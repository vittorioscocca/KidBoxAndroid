package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class NewsUiState(
    val familyId: String = "",
    val isPaid: Boolean = false,
    val feed: NewsFeed? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val filter: NewsCategory? = null,
    /** Capsula «Eventi»: solo gli eventi vicini. Esclude [filter], e viceversa. */
    val eventsOnly: Boolean = false,
    val offers: NewsOffersPayload? = null,
    val searchingOffers: Boolean = false,
    val offersError: String? = null,
)

/**
 * La scheda Notizie. Le edizioni si generano in coda sul server (40-100 s),
 * non dentro la chiamata: la prima apertura del giorno mostra quello che c'è
 * e richiama finché l'edizione non è pronta, come su iOS. Accensione, luogo,
 * lingua e offerte sono della famiglia ([NewsFamilyStore]): quello che trova
 * un membro lo leggono tutti.
 */
@HiltViewModel
class NewsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NewsRepository,
    private val briefBuilder: NewsBriefBuilder,
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
    val prefsStore: NewsPrefsStore,
    val familyStore: NewsFamilyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var loadedKey: String? = null

    init {
        viewModelScope.launch {
            val familyId = ActiveFamilyResolver.resolveFamilyId(familyDao.getAll(), familySessionPreferences.getActiveFamilyId())
            _state.update { it.copy(familyId = familyId) }
            familyStore.bind(familyId)
            prefsStore.refreshFromRemote()
            // Il piano arriva anche dopo l'apertura: si carica quando ci sono
            // tutte le condizioni, e si ricarica se cambiano le scelte.
            combine(CurrentPlanStore.plan, prefsStore.state, familyStore.state) { plan, _, fam -> plan to fam }
                .collect { (plan, fam) ->
                    _state.update { it.copy(isPaid = plan != KBPlan.FREE) }
                    if (plan != KBPlan.FREE && fam.familyId == familyId && fam.loaded && fam.settings.enabled) load(force = false)
                }
        }
    }

    /** Le scelte della famiglia, solo quando sono di questa famiglia. */
    private fun family(): NewsFamilySettings? =
        familyStore.state.value.takeIf { it.familyId == _state.value.familyId && it.loaded }?.settings

    private fun key(family: NewsFamilySettings, prefs: NewsPrefs) = listOf(
        _state.value.familyId, prefs.categories.joinToString(",") { it.id }, family.effectivePlace.label,
        family.effectiveLang, family.enabled,
    ).joinToString("|")

    fun load(force: Boolean) {
        val familyId = _state.value.familyId
        val prefs = prefsStore.current
        val family = family() ?: return
        if (familyId.isBlank() || !family.enabled || !_state.value.isPaid) return
        val key = key(family, prefs)
        if (!force && key == loadedKey && _state.value.feed != null) return
        if (!force && _state.value.feed == null) {
            repository.cachedFeed?.let { (fid, feed) ->
                if (fid == familyId && feed.dateKey == NewsDates.key(Date())) _state.update { it.copy(feed = feed) }
            }
        }
        pollJob?.cancel()
        _state.update { it.copy(loading = it.feed == null, error = null) }
        viewModelScope.launch {
            runCatching { repository.fetchFeed(familyId, family, prefs) }
                .onSuccess { feed ->
                    loadedKey = key
                    _state.update { it.copy(feed = feed, loading = false, offers = feed.offers ?: it.offers) }
                    AppAnalytics.newsOpened(context, feed.items.size, feed.events.size, feed.chargedUnits, feed.isPreparing)
                    if (feed.isPreparing) startPolling(familyId, family, prefs, key)
                    if (prefs.personalOffers && _state.value.offers == null) loadSavedOffers(familyId, family)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = (e as? NewsError)?.text ?: context.getString(R.string.news_error_unexpected)) }
                }
        }
    }

    private fun startPolling(familyId: String, family: NewsFamilySettings, prefs: NewsPrefs, key: String) {
        pollJob = viewModelScope.launch {
            repeat(MAX_POLLS) {
                delay(POLL_INTERVAL_MS)
                val feed = runCatching { repository.fetchFeed(familyId, family, prefs) }.getOrNull() ?: return@repeat
                loadedKey = key
                _state.update { it.copy(feed = feed, offers = feed.offers ?: it.offers) }
                if (!feed.isPreparing) return@launch
            }
        }
    }

    private fun loadSavedOffers(familyId: String, family: NewsFamilySettings) {
        viewModelScope.launch {
            runCatching { repository.fetchOffers(familyId, family, brief = null) }
                .onSuccess { offers -> _state.update { it.copy(offers = offers) } }
        }
    }

    fun setFilter(category: NewsCategory?) = _state.update { it.copy(filter = category, eventsOnly = false) }

    fun toggleEventsOnly() = _state.update { it.copy(filter = null, eventsOnly = !it.eventsOnly) }

    /** Le accende per tutta la famiglia. */
    fun activate() {
        familyStore.update { it.copy(enabled = true) }
        AppAnalytics.newsActivated(context)
    }

    fun searchOffers() {
        val familyId = _state.value.familyId
        val family = family() ?: return
        viewModelScope.launch {
            val brief = briefBuilder.build(familyId)
            if (brief.isEmpty) {
                _state.update { it.copy(offersError = context.getString(R.string.news_error_empty_brief)) }
                return@launch
            }
            _state.update { it.copy(searchingOffers = true, offersError = null) }
            runCatching { repository.fetchOffers(familyId, family, brief) }
                .onSuccess { offers ->
                    _state.update { it.copy(offers = offers, searchingOffers = false) }
                    AppAnalytics.newsOffersSearched(context, offers.offers.size, offers.units ?: 0)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(searchingOffers = false, offersError = (e as? NewsError)?.text ?: context.getString(R.string.news_error_unexpected))
                    }
                }
        }
    }

    fun itemOpened(kind: String, category: String, level: String) =
        AppAnalytics.newsItemOpened(context, kind, category, level)

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** ~4 minuti di richiami, poi il prossimo «aggiorna» riparte. */
        const val MAX_POLLS = 40
        const val POLL_INTERVAL_MS = 6_000L
    }
}
