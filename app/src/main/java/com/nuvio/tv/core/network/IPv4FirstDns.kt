package com.nuvio.tv.core.network

import android.util.Log
import okhttp3.Dns
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Prefers IPv4 and falls back to DNS-over-HTTPS (Cloudflare/Google by IP) when the
 * system resolver hangs or fails. Android emulators often cannot resolve public
 * hostnames via [Dns.SYSTEM] while literal IPs (10.0.2.2) still work — that
 * surfaced as infinite buffering on Torbox CDN redirects after Comet 302s.
 */
class IPv4FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.isBlank()) throw UnknownHostException("empty hostname")
        if (isIpLiteral(hostname) || hostname.equals("localhost", ignoreCase = true)) {
            return listOf(InetAddress.getByName(hostname))
        }

        val systemAddresses = trySystemLookup(hostname)
        if (systemAddresses != null) {
            return preferIpv4(systemAddresses)
        }

        val dohAddresses = tryDohLookup(hostname)
        if (dohAddresses.isNotEmpty()) {
            return preferIpv4(dohAddresses)
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\": system DNS and DoH failed")
    }

    private fun trySystemLookup(hostname: String): List<InetAddress>? {
        return try {
            DNS_EXECUTOR.submit<List<InetAddress>> {
                delegate.lookup(hostname)
            }.get(SYSTEM_DNS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "system DNS failed for $hostname: ${e.cause?.message ?: e.message}")
            null
        }
    }

    private fun tryDohLookup(hostname: String): List<InetAddress> {
        for (endpoint in DOH_ENDPOINTS) {
            try {
                val ips = queryDohARecords(endpoint, hostname)
                if (ips.isNotEmpty()) {
                    return ips.map { InetAddress.getByName(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "DoH ${endpoint.name} failed for $hostname: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun queryDohARecords(endpoint: DohEndpoint, hostname: String): List<String> {
        val encoded = URLEncoder.encode(hostname, Charsets.UTF_8.name())
        val url = endpoint.urlTemplate.replace("{name}", encoded)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", endpoint.accept)
            connectTimeout = DOH_HTTP_TIMEOUT_MS
            readTimeout = DOH_HTTP_TIMEOUT_MS
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw UnknownHostException("DoH HTTP $code from ${endpoint.name}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseDohJsonARecords(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseDohJsonARecords(body: String): List<String> {
        val root = JSONObject(body)
        val status = root.optInt("Status", -1)
        // 0 = NOERROR
        if (status != 0 && status != -1) {
            return emptyList()
        }
        val answer = root.optJSONArray("Answer") ?: return emptyList()
        val ips = ArrayList<String>(answer.length())
        for (i in 0 until answer.length()) {
            val entry = answer.optJSONObject(i) ?: continue
            // type 1 = A
            if (entry.optInt("type") != 1) continue
            val data = entry.optString("data").trim()
            if (data.isNotEmpty() && isIpLiteral(data)) {
                ips.add(data)
            }
        }
        return ips
    }

    private fun preferIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        return if (ipv4.isNotEmpty()) ipv4 else addresses.sortedBy {
            if (it is Inet4Address) 0 else 1
        }
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.matches(IPV4_LITERAL)) return true
        return value.contains(':')
    }

    private data class DohEndpoint(
        val name: String,
        val urlTemplate: String,
        val accept: String
    )

    companion object {
        private const val TAG = "IPv4FirstDns"
        private const val SYSTEM_DNS_TIMEOUT_SECONDS = 3L
        private const val DOH_HTTP_TIMEOUT_MS = 5000
        private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val DNS_EXECUTOR = Executors.newCachedThreadPool { r ->
            Thread(r, "ipv4-first-dns").apply { isDaemon = true }
        }
        // Bootstrap by literal IP so DoH works when system DNS is completely broken.
        private val DOH_ENDPOINTS = listOf(
            DohEndpoint(
                name = "cloudflare",
                urlTemplate = "https://1.1.1.1/dns-query?name={name}&type=A",
                accept = "application/dns-json"
            ),
            DohEndpoint(
                name = "google",
                urlTemplate = "https://8.8.8.8/resolve?name={name}&type=A",
                accept = "application/dns-json"
            )
        )
    }
}
