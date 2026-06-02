package it.vittorioscocca.kidbox.data.location

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GeofenceMonitorEntryPoint {
    fun geofenceMonitorRestorer(): GeofenceMonitorRestorer
}
