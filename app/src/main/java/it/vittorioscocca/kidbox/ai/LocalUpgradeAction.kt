package it.vittorioscocca.kidbox.ai

import androidx.compose.runtime.compositionLocalOf

val LocalUpgradeAction = compositionLocalOf<(String?) -> Unit> { {} }
