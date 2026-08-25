package it.vittorioscocca.kidbox.ui.screens.passwords

import android.content.Context
import androidx.annotation.StringRes
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.util.KBLog

/**
 * Gruppi password predefiniti, gemello di `PasswordGroupsService` su iOS.
 *
 * Slug, icone, colori e `sortIndex` devono restare IDENTICI a iOS: i gruppi
 * viaggiano su Firestore con id deterministico
 * `kb.password.group.{familyId}.{slug}` (vedi [PasswordGroupIds]), quindi due
 * dispositivi della stessa famiglia devono generare esattamente lo stesso
 * record — altrimenti si creerebbero doppioni a ogni sincronizzazione.
 *
 * Il nome è cifrato come qualunque altro gruppo: viene salvato TRADOTTO nella
 * lingua di chi semina, esattamente come fa iOS. È una conseguenza del fatto
 * che il nome è un dato cifrato e non una chiave di traduzione.
 */
object PasswordDefaultGroups {

    private const val PREFS_FILE = "kidbox_passwords"
    private const val SEEDED_KEY_PREFIX = "defaultGroupsSeeded."

    /** Icone in stile SF Symbol per restare allineate al dato scritto da iOS. */
    data class SeedDefinition(
        val slug: String,
        @StringRes val nameRes: Int,
        val icon: String,
        val color: String,
        val sortIndex: Int,
    )

    val seedDefinitions: List<SeedDefinition> = listOf(
        SeedDefinition(PasswordGroupIds.UNASSIGNED_SLUG, R.string.passwords_unassigned_group_label, "tray", "#8E8E93", 0),
        SeedDefinition("work", R.string.passwords_group_work, "briefcase.fill", "#0A84FF", 1),
        SeedDefinition("personal", R.string.passwords_group_personal, "person.fill", "#34C759", 2),
        SeedDefinition("social", R.string.passwords_group_social, "bubble.left.and.bubble.right.fill", "#FF9500", 3),
        SeedDefinition("finance", R.string.passwords_group_finance, "creditcard.fill", "#5E5CE6", 4),
        SeedDefinition("family", R.string.passwords_group_family, "house.fill", "#FF2D55", 5),
    )

    /**
     * Crea i gruppi predefiniti mancanti per la famiglia.
     *
     * Idempotente due volte: salta i gruppi già presenti (anche se arrivati da
     * iOS via sync) e ricorda in `SharedPreferences` di aver già seminato,
     * come il flag in `UserDefaults` su iOS.
     */
    suspend fun seedIfNeeded(
        context: Context,
        familyId: String,
        uid: String,
        passwordGroupDao: PasswordGroupDao,
        passwordCypher: PasswordCypher,
        passwordsRepository: PasswordsRepository,
    ) {
        if (familyId.isBlank() || uid.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val seededKey = SEEDED_KEY_PREFIX + familyId
        if (prefs.getBoolean(seededKey, false)) return

        val now = System.currentTimeMillis()
        var seededAny = false

        for (seed in seedDefinitions) {
            val id = PasswordGroupIds.id(familyId, seed.slug)
            // Gli id sono deterministici, quindi la sola presenza del record
            // basta a sapere che il gruppo esiste — che sia stato creato qui o
            // arrivato da iOS via sync.
            if (passwordGroupDao.getById(id) != null) continue
            runCatching {
                val entity = PasswordGroupEntity(
                    id = id,
                    familyId = familyId,
                    nameCipher = passwordCypher.encrypt(
                        context.getString(seed.nameRes),
                        familyId,
                        KBVisibilityScope.FAMILY,
                        uid,
                    ),
                    icon = seed.icon,
                    color = seed.color,
                    visibility = KBVisibilityScope.FAMILY,
                    createdBy = uid,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = null,
                    syncStateRaw = 1,
                    lastSyncError = null,
                )
                passwordGroupDao.upsert(entity)
                passwordsRepository.pushUpsertGroup(entity)
                seededAny = true
            }.onFailure {
                KBLog.data.error(
                    "seedDefaultGroups: gruppo ${seed.slug} non creato: ${it.message}",
                    "Passwords",
                )
            }
        }

        prefs.edit().putBoolean(seededKey, true).apply()
        if (seededAny) {
            KBLog.data.info("seedDefaultGroups: gruppi predefiniti creati familyId=$familyId", "Passwords")
        }
    }
}
