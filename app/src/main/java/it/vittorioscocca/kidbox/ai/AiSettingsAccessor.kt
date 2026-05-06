package it.vittorioscocca.kidbox.ai

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AiSettingsAccessor {
    fun aiSettings(): AiSettings
}

fun Context.getAiSettingsFromApp(): AiSettings =
    EntryPointAccessors.fromApplication(
        applicationContext,
        AiSettingsAccessor::class.java,
    ).aiSettings()
