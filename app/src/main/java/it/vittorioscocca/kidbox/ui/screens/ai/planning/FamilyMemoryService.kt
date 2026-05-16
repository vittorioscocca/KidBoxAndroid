package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.util.Log
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.data.local.dao.KBMemoryFactDao
import it.vittorioscocca.kidbox.data.local.entity.KBMemoryFactEntity
import it.vittorioscocca.kidbox.data.local.entity.MemoryFactCategory
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.MemoryFactRemoteStore
import it.vittorioscocca.kidbox.data.remote.ai.RemoteMemoryFactDto
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIMessageRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class FamilyMemoryService @Inject constructor(
    private val memoryFactDao: KBMemoryFactDao,
    private val memoryFactRemoteStore: MemoryFactRemoteStore,
    private val aiService: AIService,
    private val aiSettings: AiSettings,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firestoreLoadedFamilyIds = mutableSetOf<String>()

    suspend fun extractAndStore(
        familyId: String,
        conversationId: String,
        transcriptMessages: List<KBAIMessage>,
    ) {
        if (!aiSettings.isEnabled.value) {
            Log.d(TAG, "AI disabled, skip memory extract")
            return
        }
        if (familyId.isBlank()) return

        val transcript = buildTranscript(transcriptMessages)
        if (transcript.isBlank()) {
            Log.d(TAG, "empty transcript, skip")
            return
        }

        Log.i(TAG, "extract start familyId=$familyId conv=$conversationId")

        val response = aiService.sendMessage(
            messages = listOf(
                KBAIMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    roleRaw = AIMessageRole.USER.value,
                    content = transcript,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            ),
            systemPrompt = EXTRACTION_SYSTEM_PROMPT,
            familyId = familyId,
        ).getOrElse { err ->
            Log.e(TAG, "extract failed: ${err.message}")
            return
        }

        val parsed = parseExtractedFacts(response.reply)
        if (parsed.isEmpty()) {
            Log.d(TAG, "no facts parsed")
            return
        }

        val existing = memoryFactDao.getAllForFamilyAscending(familyId)
        val existingKeys = existing.map { dedupeKey(it.content) }.toSet()

        val toInsert = mutableListOf<Pair<MemoryFactCategory, String>>()
        for ((category, content) in parsed) {
            val key = dedupeKey(content)
            if (key.isEmpty() || existingKeys.contains(key)) continue
            if (toInsert.any { dedupeKey(it.second) == key }) continue
            toInsert += category to content
        }

        if (toInsert.isEmpty()) {
            Log.d(TAG, "all facts deduped")
            return
        }

        trimOldestIfNeeded(familyId, additionalCount = toInsert.size)

        val now = System.currentTimeMillis()
        val entities = toInsert.map { (category, content) ->
            KBMemoryFactEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                content = content,
                categoryRaw = category.raw,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                sourceConversationId = conversationId,
            )
        }
        memoryFactDao.upsertAll(entities)
        syncEntitiesToFirestore(entities)
        Log.i(TAG, "stored ${entities.size} facts for $familyId")
    }

    suspend fun fetchFactTexts(familyId: String): List<String> {
        if (familyId.isBlank()) return emptyList()
        return memoryFactDao.getRecentForFamily(familyId, MAX_FACTS_PER_FAMILY).map { it.content }
    }

    fun clearFirestoreLoadCache() {
        synchronized(firestoreLoadedFamilyIds) {
            firestoreLoadedFamilyIds.clear()
        }
    }

    suspend fun loadFromFirestore(familyId: String) {
        if (familyId.isBlank()) return
        synchronized(firestoreLoadedFamilyIds) {
            if (familyId in firestoreLoadedFamilyIds) return
        }

        Log.i(TAG, "Firestore load start familyId=$familyId")
        val remote = memoryFactRemoteStore.fetchAll(familyId)
        synchronized(firestoreLoadedFamilyIds) {
            firestoreLoadedFamilyIds.add(familyId)
        }
        if (remote.isEmpty()) {
            Log.i(TAG, "Firestore load empty familyId=$familyId")
            return
        }

        val localIds = memoryFactDao.getAllForFamilyAscending(familyId).map { it.id }.toSet()
        val toInsert = remote
            .filter { it.id !in localIds }
            .map { dto -> dto.toEntity() }

        if (toInsert.isEmpty()) {
            Log.i(TAG, "Firestore load OK familyId=$familyId inserted=0 remote=${remote.size}")
            return
        }

        trimOldestIfNeeded(familyId, additionalCount = toInsert.size)
        memoryFactDao.upsertAll(toInsert)
        Log.i(TAG, "Firestore load OK familyId=$familyId inserted=${toInsert.size} remote=${remote.size}")
    }

    private fun syncEntitiesToFirestore(entities: List<KBMemoryFactEntity>) {
        entities.forEach { entity ->
            syncScope.launch {
                memoryFactRemoteStore.upsert(entity)
            }
        }
    }

    private suspend fun trimOldestIfNeeded(familyId: String, additionalCount: Int) {
        val existing = memoryFactDao.getAllForFamilyAscending(familyId)
        val overflow = existing.size + additionalCount - MAX_FACTS_PER_FAMILY
        if (overflow <= 0) return
        existing.take(overflow).forEach { memoryFactDao.deleteById(it.id) }
        Log.d(TAG, "trimmed $overflow oldest facts")
    }

    private fun buildTranscript(messages: List<KBAIMessage>): String {
        val dialog = messages.filter {
            !it.isSummary && (it.roleRaw == AIMessageRole.USER.value || it.roleRaw == AIMessageRole.ASSISTANT.value)
        }
        return dialog.takeLast(20).joinToString("\n\n") { msg ->
            val label = if (msg.roleRaw == AIMessageRole.USER.value) "Utente" else "Assistente"
            "$label: ${msg.content}"
        }
    }

    private fun parseExtractedFacts(raw: String): List<Pair<MemoryFactCategory, String>> {
        val trimmed = raw.trim()
        if (trimmed.equals("NESSUN_FATTO", ignoreCase = true)) return emptyList()
        val out = mutableListOf<Pair<MemoryFactCategory, String>>()
        trimmed.lineSequence().forEach { line ->
            val row = line.trim()
            if (row.isEmpty()) return@forEach
            parseFactLine(row)?.let { out += it }
            if (out.size >= 8) return out
        }
        return out
    }

    private fun parseFactLine(line: String): Pair<MemoryFactCategory, String>? {
        if (!line.startsWith("[")) return null
        val close = line.indexOf(']')
        if (close <= 1) return null
        val categoryRaw = line.substring(1, close).trim().lowercase()
        val content = line.substring(close + 1).trim()
        if (content.isEmpty()) return null
        return MemoryFactCategory.fromRaw(categoryRaw) to content
    }

    private fun dedupeKey(content: String): String =
        content.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(DEDUPE_PREFIX_WORDS)
            .joinToString(" ")

    private fun RemoteMemoryFactDto.toEntity(): KBMemoryFactEntity {
        val now = System.currentTimeMillis()
        return KBMemoryFactEntity(
            id = id,
            familyId = familyId,
            content = content,
            categoryRaw = categoryRaw,
            createdAtEpochMillis = if (createdAtEpochMillis > 0) createdAtEpochMillis else now,
            updatedAtEpochMillis = if (updatedAtEpochMillis > 0) updatedAtEpochMillis else now,
            sourceConversationId = sourceConversationId,
        )
    }

    private companion object {
        private const val TAG = "FamilyMemoryService"
        private const val MAX_FACTS_PER_FAMILY = 25
        private const val DEDUPE_PREFIX_WORDS = 6

        private val EXTRACTION_SYSTEM_PROMPT = """
            Sei un estrattore di memoria familiare per KidBox.
            Analizza la conversazione e estrai SOLO fatti DURATURI sulla famiglia (abitudini, preferenze, problemi ricorrenti, relazioni, stili di vita).

            REGOLE:
            - Max 8 fatti, ognuno su una riga separata nel formato: [categoria] fatto
            - Categorie valide: salute, abitudini, preferenze, scuola, relazioni, casa, wallet, animali, altro
            - Ogni fatto: max 20 parole, in italiano, terza persona.
            - IGNORA: eventi one-time, dati già strutturati (visite, farmaci, calendari), informazioni temporanee.
            - INCLUDI: pattern comportamentali, preferenze espresse, osservazioni narrative.
            - Per 'casa': includi solo pattern ricorrenti (elettrodomestici problematici, abitudini di manutenzione), NON interventi one-time.
            - Per 'wallet': includi pattern di spesa, priorità dichiarate, budget abituali.
            - Per 'animali': includi salute cronica, preferenze veterinarie, abitudini.
            - Se non ci sono fatti duraturi, rispondi esattamente: NESSUN_FATTO
        """.trimIndent()
    }
}
