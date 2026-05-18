package it.vittorioscocca.kidbox.ui.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController

/**
 * Entry per [it.vittorioscocca.kidbox.ui.screens.travel.TravelPlanningViewModel] condiviso tra wizard e proposta.
 * Il route del wizard include `?destination=` con valore variabile: non usare [NavHostController.getBackStackEntry] con route esatta.
 */
fun NavHostController.travelPlanningViewModelOwner(
    familyId: String,
    fallback: NavBackStackEntry,
): NavBackStackEntry {
    val wizardRoutePrefix = "travel/$familyId/wizard"
    fun NavBackStackEntry?.isWizardEntry(): Boolean =
        this?.destination?.route?.startsWith(wizardRoutePrefix) == true

    return currentBackStack.value.lastOrNull { it.isWizardEntry() }
        ?: previousBackStackEntry.takeIf { it.isWizardEntry() }
        ?: fallback
}
