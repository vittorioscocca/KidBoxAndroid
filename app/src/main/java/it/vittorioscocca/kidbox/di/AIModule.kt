package it.vittorioscocca.kidbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBAIConversationDao
import it.vittorioscocca.kidbox.data.local.dao.KBAIMessageDao
import it.vittorioscocca.kidbox.data.repository.KBAIRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    fun provideKBAIRepository(
        conversationDao: KBAIConversationDao,
        messageDao: KBAIMessageDao,
    ): KBAIRepository = KBAIRepository(conversationDao, messageDao)

}
