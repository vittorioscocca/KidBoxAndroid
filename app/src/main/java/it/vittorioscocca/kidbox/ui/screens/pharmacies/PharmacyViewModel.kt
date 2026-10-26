package it.vittorioscocca.kidbox.ui.screens.pharmacies

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
import it.vittorioscocca.kidbox.ui.screens.news.NewsFamilySettings
import it.vittorioscocca.kidbox.ui.screens.news.NewsFamilyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PharmacyUiState(
    val familyId: String = "",
    val isPaid: Boolean = false,
    val familyLoaded: Boolean = false,
    val settings: NewsFamilySettings = NewsFamilySettings(),
    val feed: PharmacyFeed? = null,
    val loading: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Farmacie di turno (Pro e Max). Il luogo è quello delle Notizie
 * ([NewsFamilyStore]): un comune solo per tutta la famiglia. Gemello di
 * `PharmacyViewModel` in `PharmacyView.swift`.
 */
@HiltViewModel
class PharmacyViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PharmacyRepository,
    private val familyDao: KBFamilyDao,
    private val familySessionPreferences: FamilySessionPreferences,
    val familyStore: NewsFamilyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PharmacyUiState())
    val state: StateFlow<PharmacyUiState> = _state.asStateFlow()

    private var loadedKey: String? = null

    init {
        viewModelScope.launch {
            val familyId = ActiveFamilyResolver.resolveFamilyId(familyDao.getAll(), familySessionPreferences.getActiveFamilyId())
            _state.update { it.copy(familyId = familyId) }
            familyStore.bind(familyId)
            combine(CurrentPlanStore.plan, familyStore.state) { plan, fam -> plan to fam }
                .collect { (plan, fam) ->
                    val loaded = fam.loaded && fam.familyId == familyId && familyId.isNotBlank()
                    _state.update {
                        it.copy(isPaid = plan != KBPlan.FREE, familyLoaded = loaded, settings = if (loaded) fam.settings else NewsFamilySettings())
                    }
                    if (plan != KBPlan.FREE && loaded) load(force = false)
                }
        }
    }

    fun load(force: Boolean) {
        val s = _state.value
        if (s.familyId.isBlank() || !s.isPaid || !s.familyLoaded) return
        val key = "${s.familyId}|${s.settings.effectivePlace.label}"
        if (!force && key == loadedKey && s.feed != null) return
        _state.update { it.copy(loading = it.feed == null, error = null) }
        viewModelScope.launch {
            runCatching { repository.fetch(s.familyId, s.settings.effectivePlace) }
                .onSuccess { feed ->
                    loadedKey = key
                    _state.update { it.copy(feed = feed, loading = false) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = message(e)) } }
        }
    }

    fun find() = work { s ->
        val result = repository.findSource(s.familyId, s.settings.effectivePlace)
        when (result.status) {
            "not-found" -> context.getString(R.string.pharmacies_notice_not_found)
            "unreadable" -> explain(result.why)
            else -> null
        }
    }

    /** `url == null`: torna alla fonte automatica. */
    fun setSource(url: String?) = work { s ->
        val result = repository.setSource(s.familyId, s.settings.effectivePlace, url)
        if (result.status == "unreadable") explain(result.why) else null
    }

    private fun work(block: suspend (PharmacyUiState) -> String?) {
        val s = _state.value
        if (s.working || s.familyId.isBlank()) return
        _state.update { it.copy(working = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching {
                val notice = block(s)
                notice to repository.fetch(s.familyId, s.settings.effectivePlace)
            }.onSuccess { (notice, feed) ->
                _state.update { it.copy(working = false, feed = feed, notice = notice) }
            }.onFailure { e ->
                _state.update { it.copy(working = false, error = message(e)) }
            }
        }
    }

    /** Il motivo, in parole, per chi non riesce a leggere i turni. */
    private fun explain(why: String): String = context.getString(
        when (why) {
            "outdated" -> R.string.pharmacies_why_outdated
            "too-large" -> R.string.pharmacies_why_too_large
            "too-many-pages" -> R.string.pharmacies_why_too_many_pages
            "blocked" -> R.string.pharmacies_why_blocked
            "not-found" -> R.string.pharmacies_why_not_found
            "unsupported" -> R.string.pharmacies_why_unsupported
            "unreachable" -> R.string.pharmacies_error_unreachable
            else -> R.string.pharmacies_notice_unreadable
        },
    )

    private fun message(e: Throwable): String =
        (e as? PharmacyError)?.text ?: context.getString(R.string.pharmacies_error_unexpected)
}
