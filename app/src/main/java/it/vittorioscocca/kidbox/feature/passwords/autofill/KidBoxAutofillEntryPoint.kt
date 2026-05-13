package it.vittorioscocca.kidbox.feature.passwords.autofill

import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotEncryptedStore
import it.vittorioscocca.kidbox.data.passwords.AutoFillUserPreferences
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KidBoxAutofillEntryPoint {
    fun autoFillSnapshotEncryptedStore(): AutoFillSnapshotEncryptedStore
    fun familySessionPreferences(): FamilySessionPreferences
    fun firebaseAuth(): FirebaseAuth
    fun autoFillUserPreferences(): AutoFillUserPreferences
    fun passwordCypher(): PasswordCypher
    fun passwordEntryDao(): PasswordEntryDao
    fun passwordsRepository(): PasswordsRepository
}

fun android.content.Context.autofillEntryPoint(): KidBoxAutofillEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext as android.app.Application, KidBoxAutofillEntryPoint::class.java)
