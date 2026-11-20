package com.example.data.network

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Shared networking utilities for Supabase connections in Azril Clas.
 *
 * Addresses two real-world Android connectivity issues:
 * 1. Normalizes `SUPABASE_URL` even if pasted with `/rest/v1/`, `/auth/v1/`, or trailing slashes.
 * 2. Adds an automatic DNS-over-HTTPS (DoH) fallback via Cloudflare (1.1.1.1 / 1.0.0.1) and
 *    Google Public DNS (8.8.8.8 / 8.8.4.4) whenever the device's Wi-Fi / ISP DNS fails to resolve
 *    `*.supabase.co` (`UnknownHostException: No address associated with hostname`).
 */
object SupabaseNetworkClient {

    private val STRIP_PATH_SUFFIXES = listOf(
        "/rest/v1",
        "/auth/v1",
        "/functions/v1",
        "/storage/v1",
        "/realtime/v1"
    )

    private val dohBootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(ResilientSupabaseDns(primaryDns = Dns.SYSTEM, dohClient = dohBootstrapClient))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Normalizes a Supabase project URL to its root origin:
     * e.g. `https://dtavbwdnopbizlvokphd.supabase.co/rest/v1/`
     *   -> `https://dtavbwdnopbizlvokphd.supabase.co`
     */
    fun normalizeBaseUrl(rawUrl: String): String {
        var clean = rawUrl.trim().trimEnd('/')
        if (clean.isEmpty()) return ""
        val lower = clean.lowercase(Locale.US)
        for (suffix in STRIP_PATH_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                clean = clean.dropLast(suffix.length).trimEnd('/')
                break
            }
        }
        return clean
    }

    fun isConfigured(rawUrl: String, rawAnonKey: String): Boolean {
        val cleanUrl = normalizeBaseUrl(rawUrl)
        val cleanKey = rawAnonKey.trim()
        return cleanUrl.startsWith("https://") &&
            !cleanUrl.contains("YOUR_PROJECT_REF") &&
            cleanKey.length > 20 &&
            !cleanKey.contains("YOUR_SUPABASE_ANON_KEY")
    }

    fun extractHost(rawUrl: String): String {
        val normalized = normalizeBaseUrl(rawUrl)
        return normalized.toHttpUrlOrNull()?.host
            ?: normalized.removePrefix("https://").removePrefix("http://").substringBefore('/')
    }

    fun formatConnectionError(e: Exception, rawUrl: String): String {
        val host = extractHost(rawUrl).ifBlank { "server Supabase" }
        return when (e) {
            is UnknownHostException ->
                "Tidak dapat menemukan alamat server Supabase ($host). Periksa koneksi internet Anda atau pastikan proyek Supabase aktif."
            is SocketTimeoutException ->
                "Koneksi ke server Supabase ($host) kehabisan waktu. Periksa kestabilan internet Anda lalu coba lagi."
            is ConnectException ->
                "Gagal membuka koneksi ke server Supabase ($host). Periksa koneksi internet Anda."
            is SSLException ->
                "Gagal memverifikasi koneksi aman (HTTPS/SSL) ke Supabase. Pastikan tanggal & jam HP Anda akurat."
            else ->
                "Gagal terhubung ke Supabase Auth: ${e.localizedMessage ?: "kesalahan jaringan"}"
        }
    }
}

/**
 * OkHttp [Dns] implementation that tries Android's system DNS first, then automatically falls back
 * to DNS-over-HTTPS (DoH) via bootstrap IP literals (Cloudflare & Google Public DNS) when the
 * local ISP/Wi-Fi resolver fails with [UnknownHostException].
 */
internal class ResilientSupabaseDns(
    private val primaryDns: Dns = Dns.SYSTEM,
    private val dohClient: OkHttpClient,
    private val dohEndpoints: List<String> = DEFAULT_DOH_ENDPOINTS,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : Dns {

    private data class CachedEntry(
        val addresses: List<InetAddress>,
        val expiresAtMillis: Long
    )

    private val cache = ConcurrentHashMap<String, CachedEntry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim()
        if (cleanHost.isEmpty()) throw UnknownHostException("Hostname kosong")

        // 1. Fast path: normal Android / JVM system DNS
        try {
            val systemResult = primaryDns.lookup(cleanHost)
            if (systemResult.isNotEmpty()) return systemResult
        } catch (systemError: UnknownHostException) {
            // Skip DoH for local/loopback or literal IPs
            if (isLocalOrIpLiteral(cleanHost)) throw systemError

            // 2. Check TTL cache from previous DoH lookup
            val now = clockMillis()
            val cacheKey = cleanHost.lowercase(Locale.US)
            val cached = cache[cacheKey]
            if (cached != null && now < cached.expiresAtMillis) {
                return cached.addresses
            }

            // 3. Fallback: resolve via DNS-over-HTTPS using IP-literal bootstrap endpoints
            val dohResolved = resolveViaDoh(cleanHost, now)
            if (dohResolved != null && dohResolved.addresses.isNotEmpty()) {
                cache[cacheKey] = dohResolved
                return dohResolved.addresses
            }

            throw systemError
        }

        throw UnknownHostException("Tidak ada alamat IP untuk host \"$cleanHost\"")
    }

    private fun resolveViaDoh(hostname: String, nowMillis: Long): CachedEntry? {
        val encodedName = runCatching { URLEncoder.encode(hostname, "UTF-8") }.getOrElse { hostname }
        for (endpointTemplate in dohEndpoints) {
            val url = endpointTemplate.replace("{name}", encodedName)
            val entry = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/dns-json")
                    .build()
                dohClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    parseDohJson(hostname, body, nowMillis)
                }
            }.getOrNull()
            if (entry != null && entry.addresses.isNotEmpty()) {
                return entry
            }
        }
        return null
    }

    private fun parseDohJson(hostname: String, jsonBody: String, nowMillis: Long): CachedEntry? {
        if (jsonBody.isBlank()) return null
        val root = JSONObject(jsonBody)
        if (root.optInt("Status", -1) != 0) return null
        val answers = root.optJSONArray("Answer") ?: return null
        val resolved = mutableListOf<InetAddress>()
        var minTtlSeconds = DEFAULT_CACHE_TTL_SEC

        for (i in 0 until answers.length()) {
            val ans = answers.optJSONObject(i) ?: continue
            val recordType = ans.optInt("type", -1)
            val data = ans.optString("data").trim()
            if (recordType == 1) { // A (IPv4)
                val ipv4Bytes = parseIpv4Literal(data) ?: continue
                resolved.add(InetAddress.getByAddress(hostname, ipv4Bytes))
                val ttl = ans.optLong("TTL", DEFAULT_CACHE_TTL_SEC)
                if (ttl > 0L) minTtlSeconds = minOf(minTtlSeconds, ttl)
            }
        }
        if (resolved.isEmpty()) return null
        val clampedTtl = minTtlSeconds.coerceIn(MIN_CACHE_TTL_SEC, MAX_CACHE_TTL_SEC)
        return CachedEntry(
            addresses = resolved,
            expiresAtMillis = nowMillis + clampedTtl * 1000L
        )
    }

    companion object {
        private const val MIN_CACHE_TTL_SEC = 30L
        private const val DEFAULT_CACHE_TTL_SEC = 180L
        private const val MAX_CACHE_TTL_SEC = 300L

        // Bootstrap via literal IPv4 addresses whose TLS certs include IP SANs
        // so DoH succeeds even when the phone's Wi-Fi / ISP DNS is completely broken.
        val DEFAULT_DOH_ENDPOINTS = listOf(
            "https://1.1.1.1/dns-query?name={name}&type=A",
            "https://8.8.8.8/resolve?name={name}&type=A",
            "https://1.0.0.1/dns-query?name={name}&type=A",
            "https://8.8.4.4/resolve?name={name}&type=A"
        )

        internal fun parseIpv4Literal(ip: String): ByteArray? {
            val parts = ip.split('.')
            if (parts.size != 4) return null
            val bytes = ByteArray(4)
            for (i in 0..3) {
                val octet = parts[i].toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                bytes[i] = octet.toByte()
            }
            return bytes
        }

        private fun isLocalOrIpLiteral(host: String): Boolean {
            val lower = host.lowercase(Locale.US)
            if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower.endsWith(".local")) {
                return true
            }
            if (parseIpv4Literal(lower) != null) return true
            if (lower.contains(':')) return true // IPv6 literal
            return false
        }
    }
}
