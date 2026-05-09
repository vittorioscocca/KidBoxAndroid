package it.vittorioscocca.kidbox.domain.family

/**
 * UID del proprietario attuale sul documento Firestore `families/{familyId}`.
 *
 * Preferiamo **`ownerUid`** (campo usato dopo trasferimenti ownership).
 * Fallback: **`createdBy`** sul documento famiglia se il backend lo usa ancora come alias.
 *
 * Non va confuso con altri campi (es. `createdBy` su child/member altrove): qui si passa solo `snap.data` della **famiglia**.
 */
fun ownershipUidFromFamilyFirestore(data: Map<String, Any?>): String? {
    val ownerUid = (data["ownerUid"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    val docCreatedBy = (data["createdBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    return ownerUid ?: docCreatedBy
}
