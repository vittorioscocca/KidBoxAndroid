package it.vittorioscocca.kidbox.feature.passwords.autofill

import android.content.pm.PackageManager
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Risolve package → host web via Digital Asset Links (API Google pubblica),
 * per ridurre falsi negativi su app native rispetto al matching solo su package name.
 */
object NativeAppDomainResolver {

    private const val BASE =
        "https://digitalassetlinks.googleapis.com/v1/statements:list"

    fun resolveWebHostForPackage(
        packageName: String,
        packageManager: PackageManager,
    ): String? {
        if (packageName.isBlank()) return null
        val certSha256 = runCatching { sha256CertFingerprint(packageManager, packageName) }.getOrNull()
        val url = buildString {
            append(BASE)
            append("?relation=")
            append("delegate_permission/common.get_login_creds")
            append("&source.android_app.package_name=")
            append(java.net.URLEncoder.encode(packageName, Charsets.UTF_8.name()))
            if (!certSha256.isNullOrBlank()) {
                append("&source.android_app.certificate.sha256_fingerprint=")
                append(java.net.URLEncoder.encode(certSha256, Charsets.UTF_8.name()))
            }
        }
        return runCatching { fetchFirstWebSiteHost(url) }.getOrNull()
    }

    private fun sha256CertFingerprint(pm: PackageManager, packageName: String): String? {
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkg = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            pkg.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val pkg = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            pkg.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString(":") { b -> "%02X".format(Locale.US, b) }
    }

    private fun fetchFirstWebSiteHost(listUrl: String): String? {
        val conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
        }
        conn.inputStream.use { ins ->
            val text = ins.bufferedReader().readText()
            val root = JSONObject(text)
            val stmts: JSONArray = root.optJSONArray("statements") ?: return null
            for (i in 0 until stmts.length()) {
                val st = stmts.optJSONObject(i) ?: continue
                val target = st.optJSONObject("target") ?: continue
                val site = target.optJSONObject("web")?.optString("site", "")?.trim().orEmpty()
                if (site.startsWith("http")) {
                    val uri = android.net.Uri.parse(site)
                    val host = uri.host ?: continue
                    return host.lowercase(Locale.ROOT)
                }
            }
        }
        return null
    }
}
