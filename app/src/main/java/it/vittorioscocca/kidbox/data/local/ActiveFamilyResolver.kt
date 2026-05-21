package it.vittorioscocca.kidbox.data.local

import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity

/**
 * Risolve la famiglia da mostrare: prima [FamilySessionPreferences.active_family_id],
 * poi la prima riga in Room (solo bootstrap / primo avvio).
 */
object ActiveFamilyResolver {
    fun resolveFamilyId(
        families: List<KBFamilyEntity>,
        activeFamilyId: String?,
    ): String {
        val pinned = activeFamilyId?.trim()?.takeIf { it.isNotEmpty() }
        if (pinned != null) {
            families.firstOrNull { it.id == pinned }?.id?.let { return it }
        }
        return families.firstOrNull()?.id.orEmpty()
    }
}
