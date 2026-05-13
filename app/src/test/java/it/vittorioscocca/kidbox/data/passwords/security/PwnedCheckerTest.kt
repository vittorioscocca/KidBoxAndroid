package it.vittorioscocca.kidbox.data.passwords.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import it.vittorioscocca.kidbox.data.local.dao.PwnedPrefixCacheDao
import it.vittorioscocca.kidbox.data.local.entity.PwnedPrefixCacheEntity
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PwnedCheckerTest {
    private lateinit var server: MockWebServer
    private lateinit var dao: InMemoryPwnedPrefixCacheDao
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var context: Context
    private lateinit var checker: PwnedChecker
    private val clock = MutableClock(1_000_000)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dao = InMemoryPwnedPrefixCacheDao()
        networkMonitor = mock()
        whenever(networkMonitor.isOnline).thenReturn(MutableStateFlow(true))
        context = mock()
        val pm: PackageManager = mock()
        val pi = PackageInfo().apply { versionName = "1.0.0-test" }
        whenever(context.packageManager).thenReturn(pm)
        whenever(context.packageName).thenReturn("it.vittorioscocca.kidbox")
        whenever(pm.getPackageInfo("it.vittorioscocca.kidbox", 0)).thenReturn(pi)

        checker = PwnedChecker(
            http = OkHttpClient(),
            context = context,
            networkMonitor = networkMonitor,
            clock = clock,
            prefixCacheDao = dao,
            hibpBaseUrl = server.url("/").toString().removeSuffix("/"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns pwned when suffix matches`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "1E4C9B93F3F0682250B6CF8331B7EE68FD8:12917928\r\nAAAA:1",
            ),
        )
        val result = checker.check("password")
        assertEquals(PwnedChecker.Result.Pwned(12917928), result)
    }

    @Test
    fun `returns safe when suffix missing`() = runBlocking {
        server.enqueue(MockResponse().setBody("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:10\r\n"))
        val result = checker.check("password")
        assertEquals(PwnedChecker.Result.Safe, result)
    }

    @Test
    fun `returns unknown on io exception`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        val result = checker.check("password")
        assertEquals(PwnedChecker.Result.Unknown, result)
    }

    @Test
    fun `throttle enforces min 200ms between calls`() = runBlocking {
        server.enqueue(MockResponse().setBody("A:1\r\n"))
        server.enqueue(MockResponse().setBody("B:1\r\n"))
        checker.check("password")
        checker.check("password2")
        assertTrue(clock.nowMs >= 1_000_200)
    }

    @Test
    fun `cache avoids second http call same prefix`() = runBlocking {
        server.enqueue(MockResponse().setBody("1E4C9B93F3F0682250B6CF8331B7EE68FD8:10\r\n"))
        checker.check("password")
        checker.check("password")
        assertEquals(1, server.requestCount)
    }
}

private class InMemoryPwnedPrefixCacheDao : PwnedPrefixCacheDao {
    private val map = linkedMapOf<String, PwnedPrefixCacheEntity>()
    override suspend fun getByPrefix(prefix: String): PwnedPrefixCacheEntity? = map[prefix]
    override suspend fun upsert(entity: PwnedPrefixCacheEntity) {
        map[entity.prefix] = entity
    }
    override suspend fun clearAll() {
        map.clear()
    }
}

private class MutableClock(var nowMs: Long) : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant {
        val current = nowMs
        nowMs += 250
        return Instant.ofEpochMilli(current)
    }
}
