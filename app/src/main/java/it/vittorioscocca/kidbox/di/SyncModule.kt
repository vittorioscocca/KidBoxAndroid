package it.vittorioscocca.kidbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideFamilySyncCenter(
        familyDao: KBFamilyDao,
        familyMemberDao: KBFamilyMemberDao,
        childDao: KBChildDao,
    ): FamilySyncCenter = FamilySyncCenter(familyDao, familyMemberDao, childDao)
}