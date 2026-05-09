package it.vittorioscocca.kidbox.domain.family

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
        ?: memberDao.getById(uid)?.takeIf { it.familyId == familyId && !it.isDeleted }
    val byOwnerRole = membership != null &&
        membership.userId == uid &&
        membership.role.equals("owner", ignoreCase = true)
    return byFamilyField || byOwnerRole
}
