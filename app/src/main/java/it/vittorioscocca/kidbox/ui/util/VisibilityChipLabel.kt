package it.vittorioscocca.kidbox.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope

/** Etichetta localizzata della pill di visibilità (note, todo, password, wallet, documenti). */
@Composable
fun visibilityChipLabel(scope: String): String =
    stringResource(KBVisibilityScope.chipLabelRes(scope))
