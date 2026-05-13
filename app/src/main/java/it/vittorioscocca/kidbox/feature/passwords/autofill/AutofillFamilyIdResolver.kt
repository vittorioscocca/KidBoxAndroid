package it.vittorioscocca.kidbox.feature.passwords.autofill

import android.content.Context
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences

/**
 * Risolve `familyId` per servizi autofill / Credential Provider anche quando il processo
 * parte a freddo e `active_family_id` non è ancora stato scritto in prefs (ma la chiave
 * famiglia c’è già in [FamilyKeyStore]).
 */
internal fun resolveAutofillFamilyId(
    context: Context,
    sessionPrefs: FamilySessionPreferences,
    uid: String,
): String? {
    val u = uid.trim()
    if (u.isEmpty()) return null
    val active = sessionPrefs.getActiveFamilyId()?.trim().orEmpty()
    if (active.isNotEmpty()) return active
    return FamilyKeyStore.listFamilyIdsHavingKeyForUser(context, u).firstOrNull()
}
