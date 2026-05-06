package it.vittorioscocca.kidbox.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SubscriptionModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        impl: SubscriptionRepositoryImpl,
    ): SubscriptionRepository
}
