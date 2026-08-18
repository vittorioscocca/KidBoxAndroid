package it.vittorioscocca.kidbox.notifications

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VehicleReminderEntryPoint {
    fun vehicleDeadlineReminderScheduler(): VehicleDeadlineReminderScheduler
    fun vehicleDao(): VehicleDao
    fun housePaymentReminderScheduler(): HousePaymentReminderScheduler
    fun housePaymentDao(): HousePaymentDao
    fun passwordExpiryReminderScheduler(): PasswordExpiryReminderScheduler
    fun passwordEntryDao(): PasswordEntryDao
    fun passwordCypher(): PasswordCypher
}
