package it.vittorioscocca.kidbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ConnectionSpec

@Module
@InstallIn(SingletonComponent::class)
object PasswordsModule {
    @Provides
    @Singleton
    fun providePasswordSecurityOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .build()
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

}
