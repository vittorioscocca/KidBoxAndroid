package it.vittorioscocca.kidbox.di

import androidx.credentials.CredentialManager
import com.facebook.CallbackManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.remote.auth.AuthFacade
import it.vittorioscocca.kidbox.data.remote.auth.AuthProvider
import it.vittorioscocca.kidbox.data.remote.auth.AuthService
import it.vittorioscocca.kidbox.data.remote.auth.GoogleAuthService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideCredentialManager(
        @ApplicationContext context: android.content.Context,
    ): CredentialManager = CredentialManager.create(context)

    @Provides
    @Singleton
    fun provideFacebookCallbackManager(): CallbackManager = CallbackManager.Factory.create()

    @Provides
    @Singleton
    fun provideAuthFacade(
        googleAuthService: GoogleAuthService,
        firebaseAuth: FirebaseAuth,
    ): AuthFacade {
        val services: Map<AuthProvider, AuthService> = mapOf(
            AuthProvider.GOOGLE to googleAuthService,
        )
        return AuthFacade(services, firebaseAuth)
    }
}
