package it.vittorioscocca.kidbox.ui.share

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.repository.CalendarRepository
import it.vittorioscocca.kidbox.data.repository.ChatRepository
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.GroceryRepository
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class ShareActionInput(
    val familyId: String,
    val contentType: ShareContentType,
    val allUris: List<Uri>,
    val selectedDestination: ShareDestination,
    val title: String,
    val text: String,
)

@Singleton
class ShareActionHandler @Inject constructor(
    private val chatRepository: ChatRepository,
    private val noteRepository: NoteRepository,
    private val todoRepository: TodoRepository,
    private val documentRepository: DocumentRepository,
    private val photoVideoRepository: PhotoVideoRepository,
    private val groceryRepository: GroceryRepository,
    private val calendarRepository: CalendarRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val childDao: KBChildDao,
    private val todoListDao: KBTodoListDao,
) {

    suspend fun execute(input: ShareActionInput, resolver: ContentResolver) {
        when (input.selectedDestination) {
            ShareDestination.CHAT -> handleChat(input, resolver)
            ShareDestination.NOTE -> handleNote(input)
            ShareDestination.TODO -> handleTodo(input)
            ShareDestination.DOCUMENTS -> handleDocuments(input, resolver)
            ShareDestination.PHOTOS -> handlePhotos(input, resolver)
            ShareDestination.SHOPPING -> handleShopping(input)
            ShareDestination.EVENT -> handleEvent(input)
        }
    }

    private suspend fun handleChat(input: ShareActionInput, resolver: ContentResolver) {
        when (val content = input.contentType) {
            is ShareContentType.TextContent -> {
                chatRepository.sendMessage(input.familyId, ChatMessageType.TEXT, text = input.text.ifBlank { content.text })
            }
            is ShareContentType.UrlContent -> {
                chatRepository.sendMessage(input.familyId, ChatMessageType.TEXT, text = input.text.ifBlank { content.url })
            }
            is ShareContentType.ImageContent -> {
                val uris = if (input.allUris.isNotEmpty()) input.allUris else listOf(content.uri)
                if (uris.size > 1) {
                    val items = uris.mapNotNull { uri ->
                        resolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes -> bytes to false }
                    }
                    if (items.isNotEmpty()) {
                        chatRepository.sendMediaGroupMessage(input.familyId, items)
                        return
                    }
                }
                val bytes = resolver.openInputStream(content.uri)?.use { it.readBytes() } ?: return
                chatRepository.sendMessage(
                    familyId = input.familyId,
                    type = ChatMessageType.PHOTO,
                    mediaBytes = bytes,
                    fileName = resolveFileName(resolver, content.uri) ?: "photo.jpg",
                    mimeType = resolver.getType(content.uri) ?: "image/jpeg",
                )
            }
            is ShareContentType.VideoContent -> {
                val uris = if (input.allUris.isNotEmpty()) input.allUris else listOf(content.uri)
                if (uris.size > 1) {
                    val items = uris.mapNotNull { uri ->
                        resolver.openInputStream(uri)?.use { it.readBytes() }?.let { bytes -> bytes to true }
                    }
                    if (items.isNotEmpty()) {
                        chatRepository.sendMediaGroupMessage(input.familyId, items)
                        return
                    }
                }
                val bytes = resolver.openInputStream(content.uri)?.use { it.readBytes() } ?: return
                chatRepository.sendMessage(
                    familyId = input.familyId,
                    type = ChatMessageType.VIDEO,
                    mediaBytes = bytes,
                    fileName = resolveFileName(resolver, content.uri) ?: "video.mp4",
                    mimeType = resolver.getType(content.uri) ?: "video/mp4",
                )
            }
            is ShareContentType.PdfContent -> {
                val bytes = resolver.openInputStream(content.uri)?.use { it.readBytes() } ?: return
                chatRepository.sendMessage(
                    familyId = input.familyId,
                    type = ChatMessageType.DOCUMENT,
                    text = resolveFileName(resolver, content.uri) ?: "Documento",
                    mediaBytes = bytes,
                    fileName = resolveFileName(resolver, content.uri) ?: "file.pdf",
                    mimeType = "application/pdf",
                )
            }
            is ShareContentType.FileContent -> {
                val bytes = resolver.openInputStream(content.uri)?.use { it.readBytes() } ?: return
                chatRepository.sendMessage(
                    familyId = input.familyId,
                    type = ChatMessageType.DOCUMENT,
                    text = resolveFileName(resolver, content.uri) ?: "Documento",
                    mediaBytes = bytes,
                    fileName = resolveFileName(resolver, content.uri) ?: "file.bin",
                    mimeType = content.mimeType,
                )
            }
            ShareContentType.Unknown -> {
                if (input.text.isNotBlank()) {
                    chatRepository.sendMessage(input.familyId, ChatMessageType.TEXT, text = input.text)
                }
            }
        }
    }

    private suspend fun handleNote(input: ShareActionInput) {
        val title = input.title.ifBlank { "Condiviso" }
        val body = when (val content = input.contentType) {
            is ShareContentType.TextContent -> input.text.ifBlank { content.text }
            is ShareContentType.UrlContent -> input.text.ifBlank { content.url }
            else -> input.text
        }
        noteRepository.upsertNote(input.familyId, title = title, body = body)
    }

    private suspend fun handleTodo(input: ShareActionInput) {
        val childId = childDao.getChildrenByFamilyId(input.familyId).firstOrNull()?.id
            ?: error("Nessun bambino disponibile per creare il To-Do")
        val existingList = todoListDao.getByFamilyAndChild(input.familyId, childId).firstOrNull()
        val listId = existingList?.id ?: todoRepository.addList(input.familyId, childId, "Generale")
        val todoTitle = input.title.ifBlank { input.text.lineSequence().firstOrNull().orEmpty().ifBlank { "Nuovo To-Do" } }
        todoRepository.addTodo(
            familyId = input.familyId,
            childId = childId,
            listId = listId,
            title = todoTitle,
            notes = input.text.takeIf { it.isNotBlank() },
            dueAtEpochMillis = null,
            assignedTo = null,
            priorityRaw = 0,
            reminderEnabled = false,
        )
    }

    private suspend fun handleDocuments(input: ShareActionInput, resolver: ContentResolver) {
        ensureStorageAvailable(input.familyId)
        val uris = input.allUris.ifEmpty { extractSingleUri(input.contentType) }
        uris.forEach { uri ->
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@forEach
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = resolveFileName(resolver, uri) ?: "shared_file"
            documentRepository.uploadDocumentLocal(
                familyId = input.familyId,
                parentFolderId = null,
                fileName = name,
                mimeType = mime,
                bytes = bytes,
            )
        }
        documentRepository.flushPending(input.familyId)
    }

    private suspend fun handlePhotos(input: ShareActionInput, resolver: ContentResolver) {
        ensureStorageAvailable(input.familyId)
        val uris = input.allUris.ifEmpty { extractSingleUri(input.contentType) }
        uris.forEach { uri ->
            photoVideoRepository.importMediaFromUri(
                familyId = input.familyId,
                uri = uri,
                albumId = null,
                caption = input.text.takeIf { it.isNotBlank() },
            )
        }
        photoVideoRepository.flushPending(input.familyId)
    }

    private suspend fun handleShopping(input: ShareActionInput) {
        val lines = input.text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val items = if (lines.isEmpty()) listOf(input.title.ifBlank { "Nuovo elemento" }) else lines
        items.forEach { name ->
            groceryRepository.addItem(input.familyId, name, category = null, notes = null)
        }
    }

    private suspend fun handleEvent(input: ShareActionInput) {
        val now = System.currentTimeMillis()
        val start = parseDateFromText(input.text) ?: (now + 60 * 60 * 1000L)
        val end = start + 60 * 60 * 1000L
        val uid = "local"
        calendarRepository.upsertEventLocal(
            KBCalendarEventEntity(
                id = UUID.randomUUID().toString(),
                familyId = input.familyId,
                childId = null,
                title = input.title.ifBlank { "Nuovo evento" },
                notes = input.text.takeIf { it.isNotBlank() },
                location = null,
                startDateEpochMillis = start,
                endDateEpochMillis = end,
                isAllDay = false,
                categoryRaw = "family",
                recurrenceRaw = "none",
                reminderMinutes = null,
                linkedHealthItemId = null,
                linkedHealthItemType = null,
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                createdBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
        calendarRepository.flushPending(input.familyId)
    }

    private suspend fun ensureStorageAvailable(familyId: String) {
        val plan = subscriptionRepository.getPlan(familyId)
        val planBytes = plan.bytesLimit()
        if (planBytes == Long.MAX_VALUE) return
        val result = FirebaseFunctions.getInstance("europe-west1")
            .getHttpsCallable("getStorageUsage")
            .call(hashMapOf("familyId" to familyId))
            .await()
            .getData() as? Map<*, *>
        val usedBytes = (result?.get("usedBytes") as? Number)?.toLong() ?: 0L
        if (usedBytes >= planBytes) {
            error("Spazio esaurito. Aggiorna il piano per continuare.")
        }
    }

    private fun KBPlan.bytesLimit(): Long = when (this) {
        KBPlan.FREE -> 200L * 1024L * 1024L
        KBPlan.PRO -> 5L * 1024L * 1024L * 1024L
        KBPlan.MAX -> 20L * 1024L * 1024L * 1024L
    }

    private fun extractSingleUri(content: ShareContentType): List<Uri> = when (content) {
        is ShareContentType.ImageContent -> listOf(content.uri)
        is ShareContentType.VideoContent -> listOf(content.uri)
        is ShareContentType.PdfContent -> listOf(content.uri)
        is ShareContentType.FileContent -> listOf(content.uri)
        else -> emptyList()
    }

    private fun parseDateFromText(text: String): Long? {
        val match = Regex("""\b(\d{1,2})/(\d{1,2})/(\d{2,4})\b""").find(text) ?: return null
        val (d, m, yRaw) = match.destructured
        val year = if (yRaw.length == 2) "20$yRaw".toIntOrNull() else yRaw.toIntOrNull()
        val month = m.toIntOrNull()
        val day = d.toIntOrNull()
        if (year == null || month == null || day == null) return null
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun resolveFileName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return if (idx >= 0) it.getString(idx) else null
        }
    }
}

