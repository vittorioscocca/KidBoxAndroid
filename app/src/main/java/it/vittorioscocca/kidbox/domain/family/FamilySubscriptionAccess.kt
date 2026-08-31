package it.vittorioscocca.kidbox.domain.family

import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao

/**
 * Chi può avviare o cambiare l’abbonamento Play / piano famiglia:
 * coincide con quanto usa [it.vittorioscocca.kidbox.data.sync.FamilySyncCenter]:
 * proprietario sul documento famiglia (**createdBy**) *oppure* ruolo **`owner`** sul membro.
 *
 * Così non si confonde il “creatore” storico ancora leggibile solo su server con chi è ora **gestore** della famiglia.
 */
suspend fun isFamilySubscriptionManager(
    familyDao: KBFamilyDao,
    memberDao: KBFamilyMemberDao,
    familyId: String,
    uid: String,
): Boolean {
    if (familyId.isBlank() || uid.isBlank()) return false
    val family = familyDao.getById(familyId) ?: return false
    val byFamilyField = family.createdBy == uid
    val membership = memberDao.getActiveByFamilyAndUser(familyId, uid)
        ?: memberDao.getByFamilyAndId(familyId, uid)?.takeIf { !it.isDeleted }
    val byOwnerRole = membership != null &&
        membership.userId == uid &&
        membership.role.equals("owner", ignoreCase = true)
    return byFamilyField || byOwnerRole
}

/**
 * Famiglia su cui ragionare per piano, quote e permessi di acquisto.
 *
 * NON usare `familyDao.peekAnyFamilyId()` da solo: è `SELECT id FROM kb_families
 * LIMIT 1`, senza `WHERE` né `ORDER BY`. Chi è membro di una famiglia e ne ha
 * creata un'altra si ritrova, a seconda di quale riga esce per prima, il piano
 * dell'altra famiglia e il permesso di abbonarsi che non dovrebbe avere.
 * La famiglia attiva è quella scelta in sessione; il peek resta solo come
 * ultima spiaggia quando la preferenza non c'è ancora.
 */
suspend fun resolveActiveFamilyId(
    familySessionPreferences: FamilySessionPreferences,
    familyDao: KBFamilyDao,
): String =
    familySessionPreferences.getActiveFamilyId()?.takeIf { it.isNotBlank() }
        ?: familyDao.peekAnyFamilyId().orEmpty()
