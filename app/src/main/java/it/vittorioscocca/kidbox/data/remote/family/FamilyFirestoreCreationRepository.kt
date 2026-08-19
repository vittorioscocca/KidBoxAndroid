package it.vittorioscocca.kidbox.data.remote.family

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyEscrow
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.crypto.InviteCrypto
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FamilyFirestoreCreation"

/** Figlio da creare insieme alla famiglia (Firestore + Room). */
data class InitialChild(
    val id: String,
    val name: String,
    val birthDateMillis: Long?,
)

@Singleton
class FamilyFirestoreCreationRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyDao: KBFamilyDao,
    private val childDao: KBChildDao,
    @ApplicationContext private val appContext: Context,
) {

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Crea famiglia + membership + tutti i figli (uno `.set().await()` dopo l’altro).
     * Se un figlio fallisce, l’eccezione interrompe il flusso (nessun commit parziale oltre i precedenti await).
     */
    suspend fun createFamilyWithChildren(
        familyName: String,
        children: List<InitialChild>,
    ): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val familyId = UUID.randomUUID().toString()
        val familyRef = db.collection("families").document(familyId)

        val batch1 = db.batch()
        batch1.set(
            familyRef,
            mapOf(
                "name" to familyName,
                "ownerUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        val memberDoc = mutableMapOf<String, Any>(
            "uid" to uid,
            "role" to "owner",
            "createdAt" to FieldValue.serverTimestamp(),
            "isDeleted" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
        )
        auth.currentUser?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }?.let {
            memberDoc["displayName"] = it
        }
        auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.let {
            memberDoc["email"] = it
        }
        batch1.set(
            familyRef.collection("members").document(uid),
            memberDoc,
        )
        batch1.set(
            db.collection("users").document(uid)
                .collection("memberships").document(familyId),
            mapOf(
                "familyId" to familyId,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch1.commit().await()
        AppAnalytics.familyCreated(appContext)

        val now = System.currentTimeMillis()
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = familyName,
                heroPhotoURL = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = uid,
                updatedBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = now,
                lastSyncError = null,
            ),
        )

        val toSave = children.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() }
        for (child in toSave) {
            KBLog.data.debug("Inviando figlio ${child.name} con ID ${child.id}", "DEBUG_SAVE")
            val childData = mutableMapOf<String, Any>(
                "name" to child.name,
                "isDeleted" to false,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (child.birthDateMillis != null) {
                childData["birthDate"] = Timestamp(
                    child.birthDateMillis / 1000,
                    ((child.birthDateMillis % 1000) * 1_000_000).toInt(),
                )
            }
            familyRef.collection("children").document(child.id).set(childData).await()
            childDao.upsert(
                KBChildEntity(
                    id = child.id,
                    familyId = familyId,
                    name = child.name,
                    birthDateEpochMillis = child.birthDateMillis,
                    weightKg = null,
                    heightCm = null,
                    createdBy = uid,
                    createdAtEpochMillis = now,
                    updatedBy = uid,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        // ── Master key della famiglia ──────────────────────────────────────
        // Nasce QUI, insieme alla famiglia, come su iOS (SetupFamilyView e
        // creazione da onboarding). Prima veniva creata pigramente da
        // `InviteWrapService.createInvite`, cioè solo alla generazione del primo
        // invito: una famiglia creata da Impostazioni restava senza chiave, e il
        // creatore non poteva usare password, documenti, wallet e allegati chat
        // — tutte operazioni che fallivano con `MissingFamilyKeyException`.
        //
        // Il backup su escrow è immediato e non rimandato al primo invito, così
        // il creatore recupera la propria chiave anche se reinstalla l'app senza
        // aver mai invitato nessuno.
        //
        // Best effort: se fallisce, la famiglia è comunque creata e
        // `InviteWrapService` genererà la chiave al primo invito, come prima.
        runCatching {
            val newKey = InviteCrypto.generateFamilyKey()
            FamilyKeyStore.saveFamilyKey(appContext, newKey, familyId, uid)
            FamilyKeyEscrow.backupRawKey(newKey, familyId, uid)
            KBLog.crypto.info("master key created and backed up familyId=$familyId", TAG)
        }.onFailure {
            KBLog.crypto.error("master key creation failed familyId=$familyId: ${it.message}", TAG, it)
        }

        KBLog.data.info("createFamilyWithChildren OK familyId=$familyId childrenWritten=${toSave.size}", TAG)
        return familyId
    }

    /** Compatibilità onboarding: un solo figlio. */
    suspend fun createFamilyWithInitialChild(
        familyName: String,
        childName: String,
        birthDateMillis: Long?,
    ): String {
        val childId = UUID.randomUUID().toString()
        return createFamilyWithChildren(
            familyName,
            listOf(InitialChild(id = childId, name = childName, birthDateMillis = birthDateMillis)),
        )
    }
}
