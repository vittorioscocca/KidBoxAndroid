package it.vittorioscocca.kidbox.data.remote

/** Compatibilità chiamanti Storage: equivale a [AppCheckTokenCache.getToken] senza forzare refresh. */
suspend fun prefetchAppCheckTokenForStorage() {
    AppCheckTokenCache.getToken(forceRefresh = false)
}
