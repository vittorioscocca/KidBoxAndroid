package it.vittorioscocca.kidbox.di

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.billing.KBBillingManager
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {

    @Provides
    @Singleton
    fun provideBillingClient(@ApplicationContext context: Context): BillingClient =
        BillingClient.newBuilder(context)
            .setListener { _, _ -> }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance("europe-west1")

    @Provides
    @Singleton
    fun provideKBBillingManager(
        @ApplicationContext context: Context,
        subscriptionRepository: SubscriptionRepository,
        familyDao: KBFamilyDao,
        auth: FirebaseAuth,
    ): KBBillingManager = KBBillingManager(context, subscriptionRepository, familyDao, auth)
}
