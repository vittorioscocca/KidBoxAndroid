package it.vittorioscocca.kidbox.ui.screens.work

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.docintel.DocIntelBadResponseException
import it.vittorioscocca.kidbox.data.docintel.DocIntelUnreadableException
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkRecordEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.repository.CalendarRepository
import it.vittorioscocca.kidbox.data.work.PendingWorkShare
import it.vittorioscocca.kidbox.data.work.WorkAttachmentService
import it.vittorioscocca.kidbox.data.work.WorkDocumentService
import it.vittorioscocca.kidbox.data.work.WorkNotAWorkDocumentException
import it.vittorioscocca.kidbox.data.repository.WorkRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.work.WorkFields
import it.vittorioscocca.kidbox.domain.work.WorkLeavePeriod
import it.vittorioscocca.kidbox.domain.work.WorkRecordKind
import it.vittorioscocca.kidbox.util.KBLog
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Documento scelto o condiviso e non ancora letto. */
data class WorkStaged(val file: File, val mime: String)

/** Bozza di conferma (nuovo documento o modifica di uno salvato). */
data class WorkDraftUi(
    val kind: String,
    val fields: WorkFields,
    val referenceDate: LocalDate,
    val file: File? = null,
    val mime: String = "",
    /** Valorizzato = modifica di un documento già salvato (nessun nuovo file). */
    val recordId: String? = null,
    /** I campi com'erano prima della modifica (stessi id dei periodi della bozza). */
    val base: WorkFields? = null,
)

data class WorkMember(val uid: String, val name: String)

data class WorkUiState(
    val familyId: String = "",
    val me: String = "",
    val myName: String = "",
    val isPaid: Boolean = false,
    val members: List<WorkMember> = emptyList(),
    val profiles: List<KBWorkProfileEntity> = emptyList(),
    val records: List<KBWorkRecordEntity> = emptyList(),
    val selectedOwner: String? = null,
    val staged: WorkStaged? = null,
    val isAnalyzing: Boolean = false,
    val draft: WorkDraftUi? = null,
    val message: String? = null,
    val sharedDropped: Boolean = false,
) {
    val owner: String get() = selectedOwner ?: me
    val isMine: Boolean get() = owner == me
    val myProfile: KBWorkProfileEntity? get() = profiles.firstOrNull { it.ownerUid == me }

    /** Io, e gli adulti che mi hanno condiviso la loro scheda. */
    val owners: List<String>
        get() = listOf(me) + profiles
            .filter { it.ownerUid != me && it.visibilityScope == KBVisibilityScope.MEMBERS && me in decodeStringList(it.visibilityMemberIdsJson) }
            .map { it.ownerUid }

    fun nameOf(uid: String, mine: String, other: String): String =
        if (uid == me) mine else members.firstOrNull { it.uid == uid }?.name ?: other
}

@HiltViewModel
class WorkViewModel @Inject constructor(
    private val repository: WorkRepository,
    private val documentService: WorkDocumentService,
    private val attachments: WorkAttachmentService,
    private val calendarRepository: CalendarRepository,
    private val calendarDao: KBCalendarEventDao,
    private val memberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkUiState())
    val state: StateFlow<WorkUiState> = _state.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null

    private val labels = WorkDocumentService.LeaveLabels(
        ferie = context.getString(R.string.work_leave_default),
        permessoRol = context.getString(R.string.work_leave_rol),
        malattia = context.getString(R.string.work_leave_sick),
    )

    fun bind(familyId: String) {
        if (familyId.isBlank() || (boundFamilyId == familyId && observeJob != null)) return
        boundFamilyId = familyId
        val uid = auth.currentUser?.uid.orEmpty()
        val name = auth.currentUser?.displayName?.trim().orEmpty().ifBlank { context.getString(R.string.work_me) }
        _state.update { it.copy(familyId = familyId, me = uid, myName = name) }
        repository.startRealtime(familyId)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                repository.observeProfiles(familyId),
                repository.observeRecords(familyId),
                memberDao.observeActiveByFamilyId(familyId),
                CurrentPlanStore.plan,
            ) { profiles, records, members, plan ->
                val named = members.map { WorkMember(it.userId, it.displayName?.takeIf { n -> n.isNotBlank() } ?: context.getString(R.string.work_family_member)) }
                val myName = named.firstOrNull { it.uid == uid }?.name ?: name
                _state.update {
                    it.copy(
                        profiles = profiles, records = records, members = named,
                        myName = myName, isPaid = plan != KBPlan.FREE,
                    )
                }
            }.collect { }
        }
        consumeSharedFile()
    }

    fun dismissMessage() = _state.update { it.copy(message = null, sharedDropped = false) }
    fun selectOwner(uid: String) = _state.update { it.copy(selectedOwner = uid) }

    fun fields(record: KBWorkRecordEntity): WorkFields? = repository.open(record)
    fun employer(profile: KBWorkProfileEntity): String = repository.employer(profile)

    // ── Documento in attesa ─────────────────────────────────────────────

    /** File arrivato dal foglio di condivisione: in attesa nella scheda, non letto. Sul Free si scarta. */
    fun consumeSharedFile() {
        val file = PendingWorkShare.take() ?: return
        if (CurrentPlanStore.plan.value == KBPlan.FREE) {
            file.parentFile?.deleteRecursively()
            _state.update { it.copy(sharedDropped = true) }
            return
        }
        stageFile(file)
    }

    /** Un `Uri` scelto (fotocamera, libreria, file, Documenti di KidBox) → copia nella cache, in attesa. */
    fun stageUri(uri: Uri) {
        viewModelScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val name = displayName(uri) ?: "${context.getString(R.string.work_photo)}.jpg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("unreadable")
                    val dir = File(context.cacheDir, "work-import-${UUID.randomUUID()}").apply { mkdirs() }
                    File(dir, name.replace('/', '-').replace(':', '-').ifBlank { "documento" }).apply { writeBytes(bytes) }
                }
            }.getOrNull()
            if (file == null) _state.update { it.copy(message = context.getString(R.string.work_error_unreadable)) }
            else stageFile(file)
        }
    }

    fun stageFile(file: File) {
        discardStaged()
        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        _state.update { it.copy(staged = WorkStaged(file, mime), message = null) }
    }

    fun discardStaged() {
        _state.value.staged?.file?.parentFile?.deleteRecursively()
        _state.update { it.copy(staged = null) }
    }

    /** «Leggi il documento»: l'unico punto in cui parte la lettura (e il messaggio AI che costa). */
    fun readStaged() {
        val s = _state.value.staged ?: return
        if (_state.value.isAnalyzing) return
        _state.update { it.copy(isAnalyzing = true, message = null) }
        viewModelScope.launch {
            documentService.analyze(s.file, s.mime, _state.value.familyId, labels)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isAnalyzing = false, staged = null,
                            draft = WorkDraftUi(result.kind, result.fields, result.referenceDate, s.file, s.mime),
                        )
                    }
                }
                .onFailure { err ->
                    val msg = when (err) {
                        is WorkNotAWorkDocumentException -> context.getString(R.string.work_error_not_work)
                        is DocIntelUnreadableException -> context.getString(R.string.work_error_only_pdf_images)
                        is DocIntelBadResponseException -> context.getString(R.string.work_error_bad_response)
                        else -> err.message ?: context.getString(R.string.work_error_generic)
                    }
                    _state.update { it.copy(isAnalyzing = false, message = msg) }
                }
        }
    }

    // ── Bozza ───────────────────────────────────────────────────────────

    fun updateDraft(transform: (WorkDraftUi) -> WorkDraftUi) = _state.update { s -> s.copy(draft = s.draft?.let(transform)) }
    fun discardDraft() {
        // Il file in cache resta se è ancora in lettura da un salvataggio; qui si scarta una bozza non salvata.
        _state.value.draft?.takeIf { it.recordId == null }?.file?.parentFile?.deleteRecursively()
        _state.update { it.copy(draft = null) }
    }

    fun startEdit(record: KBWorkRecordEntity) {
        viewModelScope.launch {
            val raw = repository.open(record) ?: return@launch
            val f = linkLegacyEvents(raw)
            _state.update {
                it.copy(
                    draft = WorkDraftUi(
                        kind = record.kind, fields = f,
                        referenceDate = java.time.Instant.ofEpochMilli(record.referenceDateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate(),
                        recordId = record.id, base = f,
                    ),
                )
            }
        }
    }

    /** I fogli senza `eventId` per periodo (scritti da un client più vecchio): si ritrova l'evento dalle date. */
    private suspend fun linkLegacyEvents(input: WorkFields): WorkFields {
        if (input.eventIds.isEmpty() || input.leavePeriods.all { it.eventId != null }) return input
        val events = input.eventIds.mapNotNull { calendarDao.getById(it) }.filter { !it.isDeleted }
        val claimed = input.leavePeriods.mapNotNull { it.eventId }.toMutableSet()
        val periods = input.leavePeriods.map { p ->
            if (p.eventId != null) return@map p
            val (s, e) = eventSpan(p) ?: return@map p
            val ev = events.firstOrNull { it.id !in claimed && sameDay(it.startDateEpochMillis, s) && sameDay(it.endDateEpochMillis, e) }
            if (ev != null) { claimed += ev.id; p.copy(eventId = ev.id, inCalendar = true) } else p
        }
        return input.copy(leavePeriods = periods)
    }

    // ── Salvataggio ─────────────────────────────────────────────────────

    fun saveDraft() {
        val d = _state.value.draft ?: return
        val s = _state.value
        viewModelScope.launch {
            runCatching {
                if (d.recordId != null) applyEdit(d, s) else createRecord(d, s)
            }.onSuccess {
                _state.update { it.copy(draft = null) }
            }.onFailure { err ->
                KBLog.data.warning("work save failed: ${err.message}", "WorkViewModel")
                _state.update { it.copy(message = context.getString(R.string.work_error_save)) }
            }
        }
    }

    private suspend fun createRecord(d: WorkDraftUi, s: WorkUiState) {
        val profile = repository.ensureProfile(s.familyId, s.myName)
        val shared = if (profile.visibilityScope == KBVisibilityScope.MEMBERS) decodeStringList(profile.visibilityMemberIdsJson) else emptyList()
        var fields = d.fields
        if (d.kind == WorkRecordKind.LEAVE_PLAN) {
            fields = fields.copy(leavePeriods = createLeaveEvents(s, fields.leavePeriods))
            fields = fields.copy(eventIds = fields.leavePeriods.mapNotNull { it.eventId })
        }
        val file = d.file ?: error("file mancante")
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val attachmentId = attachments.upload(
            familyId = s.familyId, ownerUid = s.me, fileName = file.name, mimeType = d.mime, bytes = bytes,
            sharedWith = shared, folderTitle = context.getString(R.string.work_folder_title),
        )
        repository.saveRecord(
            familyId = s.familyId, recordId = null, kind = d.kind,
            referenceDateEpochMillis = WorkFields.startOfDayMillis(d.referenceDate),
            fields = fields, attachmentDocumentId = attachmentId, name = s.myName,
        ).getOrThrow()
        repository.setEmployerIfMissing(s.familyId, fields.employer, s.myName)
        calendarRepository.flushPending(s.familyId)
        file.parentFile?.deleteRecursively()
    }

    private suspend fun applyEdit(d: WorkDraftUi, s: WorkUiState) {
        val rid = d.recordId ?: return
        val record = s.records.firstOrNull { it.id == rid } ?: return
        var fields = d.fields
        var reference = d.referenceDate
        if (record.kind == WorkRecordKind.LEAVE_PLAN) {
            fields = reconcileLeaveEvents(s, d.base, fields)
            fields.leavePeriods.mapNotNull { it.startDay }.minOrNull()?.let { reference = it }
        }
        repository.saveRecord(
            familyId = s.familyId, recordId = rid, kind = record.kind,
            referenceDateEpochMillis = WorkFields.startOfDayMillis(reference),
            fields = fields, attachmentDocumentId = null, name = s.myName,
        ).getOrThrow()
        calendarRepository.flushPending(s.familyId)
    }

    // ── Eventi del foglio ferie ─────────────────────────────────────────

    private fun eventTitle(p: WorkLeavePeriod, ownerName: String): String =
        "${WorkLeavePeriod.cleanLabel(p.label, labels.ferie, labels.permessoRol, labels.malattia)} - $ownerName"

    /** Il calendario chiude i tutto-il-giorno alle 23:59:59 dell'ultimo giorno. */
    private fun eventSpan(p: WorkLeavePeriod): Pair<Long, Long>? {
        val first = p.startDay ?: return null
        val last = (p.endDay ?: first).let { if (it.isBefore(first)) first else it }
        val start = WorkFields.startOfDayMillis(first)
        val end = WorkFields.startOfDayMillis(last.plusDays(1)) - 1
        return start to end
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val z = ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(a).atZone(z).toLocalDate() == java.time.Instant.ofEpochMilli(b).atZone(z).toLocalDate()
    }

    private suspend fun createLeaveEvents(s: WorkUiState, periods: List<WorkLeavePeriod>): List<WorkLeavePeriod> =
        periods.map { p ->
            if (!p.inCalendar || p.eventId != null) return@map p
            val (start, end) = eventSpan(p) ?: return@map p
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            calendarRepository.upsertEventLocal(
                KBCalendarEventEntity(
                    id = id, familyId = s.familyId, childId = null,
                    title = eventTitle(p, s.myName), notes = null, location = null,
                    startDateEpochMillis = start, endDateEpochMillis = end, isAllDay = true,
                    categoryRaw = "family", recurrenceRaw = "none", reminderMinutes = null,
                    linkedHealthItemId = null, linkedHealthItemType = null,
                    visibilityScope = KBVisibilityScope.FAMILY, visibilityMemberIdsJson = "[]",
                    isDeleted = false, createdAtEpochMillis = now, updatedAtEpochMillis = now,
                    updatedBy = s.me, createdBy = s.me,
                    syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue, lastSyncError = null,
                ),
            )
            p.copy(eventId = id)
        }

    private suspend fun removeCalendarEvent(id: String) {
        calendarDao.getById(id)?.let { calendarRepository.deleteEventLocal(it) }
    }

    /**
     * Dopo una modifica: un periodo acceso e già in calendario si sposta, uno
     * acceso e nuovo si crea, uno spento o tolto si revoca dal calendario.
     */
    private suspend fun reconcileLeaveEvents(s: WorkUiState, old: WorkFields?, input: WorkFields): WorkFields {
        val keptIds = input.leavePeriods.map { it.id }.toSet()
        old?.leavePeriods.orEmpty().filter { it.id !in keptIds }.forEach { p -> p.eventId?.let { removeCalendarEvent(it) } }
        val updated = input.leavePeriods.map { p ->
            if (p.inCalendar) {
                val ev = p.eventId?.let { calendarDao.getById(it) }?.takeIf { !it.isDeleted }
                val span = eventSpan(p)
                if (ev != null && span != null) {
                    calendarRepository.upsertEventLocal(
                        ev.copy(title = eventTitle(p, s.myName), startDateEpochMillis = span.first, endDateEpochMillis = span.second),
                    )
                    p
                } else p.copy(eventId = null)   // l'evento non c'è più: si ricrea
            } else {
                p.eventId?.let { removeCalendarEvent(it) }
                p.copy(eventId = null)
            }
        }
        val withNew = createLeaveEvents(s, updated)
        return input.copy(leavePeriods = withNew, eventIds = withNew.mapNotNull { it.eventId })
    }

    // ── Eliminazione, condivisione, file ────────────────────────────────

    fun deleteRecord(record: KBWorkRecordEntity) {
        viewModelScope.launch {
            val f = repository.open(record)
            if (record.kind == WorkRecordKind.LEAVE_PLAN && f != null) {
                f.eventIds.forEach { removeCalendarEvent(it) }
                f.leavePeriods.mapNotNull { it.eventId }.filter { it !in f.eventIds }.forEach { removeCalendarEvent(it) }
            }
            record.attachmentDocumentId?.let { attachments.delete(it) }
            repository.deleteRecord(record)
            calendarRepository.flushPending(record.familyId)
        }
    }

    fun setSharing(memberIds: List<String>) {
        val s = _state.value
        viewModelScope.launch {
            runCatching {
                repository.setSharing(s.familyId, memberIds, s.myName)
                attachments.syncVisibility(s.familyId, s.me, memberIds.filter { it != s.me })
            }.onFailure { _state.update { st -> st.copy(message = context.getString(R.string.work_error_save)) } }
        }
    }

    fun ensureProfile() {
        val s = _state.value
        viewModelScope.launch { runCatching { repository.ensureProfile(s.familyId, s.myName) } }
    }

    fun openFile(record: KBWorkRecordEntity, onFile: (File, String) -> Unit) {
        val id = record.attachmentDocumentId ?: return
        viewModelScope.launch {
            attachments.open(id)
                .onSuccess { file ->
                    val mime = when (file.extension.lowercase()) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "image/*" }
                    onFile(file, mime)
                }
                .onFailure { _state.update { it.copy(message = context.getString(R.string.work_error_open_file)) } }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    override fun onCleared() {
        // I listener di Lavoro servono anche alla Home e al quaderno: non si fermano qui.
        super.onCleared()
    }
}
