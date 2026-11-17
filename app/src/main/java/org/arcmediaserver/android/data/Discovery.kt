package org.arcmediaserver.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** What `GET /api/v1/discover` answers: only on the home network, and without signing in. */
@Serializable
data class DiscoverAnswer(
    /** Always "arc", so other servers on the port aren't mistaken for Arc. */
    val product: String = "",
    val id: String = "",
    val name: String = "",
    val version: String = "",
    @SerialName("api_version") val apiVersion: Int = 0,
)

/** An Arc found on the home network, with the address to connect to. */
data class FoundServer(val id: String, val name: String, val version: String, val address: String)

/**
 * Finds Arc on the home network without typing its address (arc-media-server `GET /discover`).
 * TV apps can't listen for broadcasts, so, as the server's docs say, the TV tries the addresses
 * on its own network on Arc's port, and lists the servers that answer as Arc.
 */
object Discovery {
    /** Arc's default port (`ServerAddress.DEFAULT_PORT`). */
    const val PORT = 8099

    /**
     * The addresses to try for this TV's IPv4 [address] with [prefix] bits of network: the
     * network's hosts, but never more than the TV's own /24 (a bigger network would be thousands of
     * tries), and never the TV itself, the network's own address or its broadcast address.
     */
    fun candidates(address: ByteArray, prefix: Int): List<String> {
        if (address.size != 4 || prefix !in 1..32) return emptyList()
        val bits = maxOf(prefix, 24)
        if (bits >= 31) return emptyList()
        val ip = address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xff) }
        val mask = (0xffffffffL shl (32 - bits)) and 0xffffffffL
        val network = ip and mask
        val broadcast = network or (mask.inv() and 0xffffffffL)
        return ((network + 1) until broadcast).filter { it != ip }.map { n ->
            "${(n shr 24) and 0xff}.${(n shr 16) and 0xff}.${(n shr 8) and 0xff}.${n and 0xff}"
        }
    }

    /** A private (home network) IPv4 address: 10/8, 172.16/12 or 192.168/16. */
    fun isPrivate(address: ByteArray): Boolean {
        if (address.size != 4) return false
        val a = address[0].toInt() and 0xff
        val b = address[1].toInt() and 0xff
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    /**
     * Asks each host in [hosts] on [port], a few dozen at a time with short timeouts, and calls
     * [onFound] for each Arc as it answers (each server once, by its id). Returns them all, by name.
     */
    suspend fun scan(
        hosts: List<String>,
        client: OkHttpClient,
        port: Int = PORT,
        parallel: Int = 48,
        onFound: (FoundServer) -> Unit = {},
    ): List<FoundServer> = withContext(Dispatchers.IO) {
        val quick = client.newBuilder()
            .connectTimeout(600, TimeUnit.MILLISECONDS)
            .readTimeout(1_200, TimeUnit.MILLISECONDS)
            .callTimeout(2_000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val gate = Semaphore(parallel)
        val seen = java.util.concurrent.ConcurrentHashMap<String, FoundServer>()
        coroutineScope {
            hosts.map { host ->
                async {
                    gate.withPermit {
                        val found = ask(quick, host, port) ?: return@withPermit
                        if (seen.putIfAbsent(found.id.ifEmpty { found.address }, found) == null) onFound(found)
                    }
                }
            }.awaitAll()
        }
        seen.values.sortedBy { it.name.lowercase() }
    }

    private fun ask(client: OkHttpClient, host: String, port: Int): FoundServer? {
        val address = "http://$host:$port"
        val request = Request.Builder().url("$address/api/v1/discover").header("Accept", "application/json").build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val answer = ArcJson.decodeFromString(DiscoverAnswer.serializer(), response.body.string())
                if (answer.product != "arc" || answer.apiVersion < 1) return null
                FoundServer(answer.id, answer.name.ifBlank { host }, answer.version, address)
            }
        } catch (_: Exception) {
            // Nothing there, not Arc, or too slow: most addresses on a network are like this.
            null
        }
    }
}
