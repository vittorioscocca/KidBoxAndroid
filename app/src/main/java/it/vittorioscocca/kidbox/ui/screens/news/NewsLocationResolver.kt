package it.vittorioscocca.kidbox.ui.screens.news

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.util.KBLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * La città per le Notizie: dalla posizione attuale (una sola lettura, a
 * precisione «città», solo su richiesta) o da un nome scritto a mano. Si
 * salvano paese, regione, provincia e città — mai le coordinate.
 */
@Singleton
class NewsLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Il permesso lo chiede la schermata prima di chiamare qui. */
    @SuppressLint("MissingPermission")
    suspend fun currentPlace(): NewsPlace? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        }.getOrNull() ?: runCatching { client.lastLocation.await() }.getOrNull() ?: return null
        val address = reverse(location.latitude, location.longitude) ?: return null
        return place(address)
    }

    suspend fun place(cityName: String): NewsPlace? {
        val query = cityName.trim().takeIf { it.isNotEmpty() } ?: return null
        val address = forward(query) ?: return null
        return place(address)
    }

    private fun place(a: Address): NewsPlace? {
        val code = a.countryCode?.uppercase()?.takeIf { it.length == 2 } ?: return null
        return NewsPlace(
            countryCode = code,
            country = a.countryName ?: code,
            region = a.adminArea ?: "",
            province = a.subAdminArea ?: "",
            city = a.locality ?: a.subAdminArea ?: "",
        )
    }

    private suspend fun reverse(lat: Double, lon: Double): Address? {
        val geocoder = Geocoder(context, KBLocale.current())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    private suspend fun forward(query: String): Address? {
        val geocoder = Geocoder(context, KBLocale.current())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                    override fun onError(errorMessage: String?) = cont.resume(null)
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(query, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }
}
