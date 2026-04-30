package it.vittorioscocca.kidbox.data.health

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "HealthFolderResolver"
private const val SALUTE_TITLE = "Salute"
private const val REFERTI_TITLE = "Referti"

@Singleton
class HealthFolderResolver @Inject constructor(
    private val categoryDao: KBDocumentCategoryDao,
    private val documentRepository: DocumentRepository,
    private val auth: FirebaseAuth,
) {
    private val mutex = Mutex()

    /** Returns (saluteFolder, refertiFolder), creating them locally if they don't yet exist.
     *  Idempotent: safe to call on every upload. The created folders carry PENDING_UPSERT so
     *  DocumentRepository.flushPending will push them to Firestore. */
    suspend fun ensureHealthFolders(
        familyId: String,
    ): Pair<KBDocumentCategoryEntity, KBDocumentCategoryEntity> = mutex.withLock {
        val all = categoryDao.getAllByFamilyId(familyId)

        val salute = all.firstOrNull { !it.isDeleted && it.parentId == null && it.title == SALUTE_TITLE }
            ?: run {
                val nextSort = (all.filter { it.parentId == null }.maxOfOrNull { it.sortOrder } ?: 0) + 1
                Log.d(TAG, "Creating Salute root folder familyId=$familyId sortOrder=$nextSort")
                documentRepository.createFolderLocal(
                    familyId = familyId,
                    title = SALUTE_TITLE,
                    parentId = null,
                    sortOrder = nextSort,
                )
            }

        val referti = all.firstOrNull { !it.isDeleted && it.parentId == salute.id && it.title == REFERTI_TITLE }
            ?: run {
                val nextSort = (all.filter { it.parentId == salute.id }.maxOfOrNull { it.sortOrder } ?: 0) + 1
                Log.d(TAG, "Creating Referti sub-folder saluteId=${salute.id} sortOrder=$nextSort")
                documentRepository.createFolderLocal(
                    familyId = familyId,
                    title = REFERTI_TITLE,
                    parentId = salute.id,
                    sortOrder = nextSort,
                )
            }

        salute to referti
    }
}
