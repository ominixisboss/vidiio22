package com.ominix.vidiio.data.http

import android.content.Context
import android.net.http.X509TrustManagerExtensions
import java.io.ByteArrayInputStream
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * A trust manager that asks the device's own certificate store first, and, only if that rejects a chain,
 * the public root certificates bundled in res/raw. Old TVs and sticks have stale stores that fail with
 * "trust anchor for certification path not found" for sites signed by newer roots.
 *
 * This is not "trust everything": a chain still has to validate, with correct dates and signatures, up to
 * one of those roots, and host names are still checked by the TLS stack as before.
 */
class BundledRootsTrust private constructor(
    private val system: X509TrustManager,
    private val bundled: X509TrustManager?,
) : X509ExtendedTrustManager() {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        verify(chain, authType, null)

    // Android's own trust manager (with the app's network security config) insists on knowing the host, and
    // the TLS stack calls these two variants to supply it.
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        verify(chain, authType, (socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket?.inetAddress?.hostName)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        verify(chain, authType, engine?.handshakeSession?.peerHost ?: engine?.peerHost)

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        system.acceptedIssuers + (bundled?.acceptedIssuers ?: emptyArray())

    private fun verify(chain: Array<X509Certificate>, authType: String, host: String?) {
        try {
            checkWithSystem(chain, authType, host)
        } catch (first: CertificateException) {
            val fallback = bundled ?: throw first
            try {
                fallback.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                throw first
            }
        }
    }

    private fun checkWithSystem(chain: Array<X509Certificate>, authType: String, host: String?) {
        if (host != null) {
            // Hostname-aware check: validates the chain and applies the network security config for that host.
            X509TrustManagerExtensions(system).checkServerTrusted(chain, authType, host)
        } else {
            system.checkServerTrusted(chain, authType)
        }
    }

    companion object {
        /** For tests: a manager over explicit system and bundled trust managers. */
        internal fun forTesting(system: X509TrustManager, bundled: X509TrustManager?) = BundledRootsTrust(system, bundled)

        /** An SSL context that uses the trust manager, and the trust manager itself. */
        fun create(context: Context): Pair<SSLContext, X509TrustManager> {
            val system = trustManagerFor(null)
            val bundled = runCatching { loadBundled(context) }.getOrNull()
                ?.takeIf { it.size() > 0 }
                ?.let { trustManagerFor(it) }
            val manager = BundledRootsTrust(system, bundled)
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(manager), null) }
            return ssl to manager
        }

        /** res/raw file names (without extension) of the bundled roots; keep in step with network_security_config.xml. */
        private val BUNDLED_ROOTS = listOf("amazon_root_1","amazon_root_2","amazon_root_3","amazon_root_4","digicert_global_g2","digicert_global_g3","digicert_tls_ecc_g5","digicert_tls_rsa_g5","globalsign_e46","globalsign_r3","globalsign_r46","gts_root_r1","gts_root_r3","gts_root_r4","isrg_root_x1","isrg_root_x2","sectigo_e46","sectigo_r46","starfield_services_g2","usertrust_ecc","usertrust_rsa")

        internal fun trustManagerFor(store: KeyStore?): X509TrustManager {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(store)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        private fun loadBundled(context: Context): KeyStore {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val factory = CertificateFactory.getInstance("X.509")
            // Looked up by name, not through the R class: release builds shrink and may rewrite R, and a
            // reflective walk of R.raw would silently find nothing there.
            BUNDLED_ROOTS.forEachIndexed { index, name ->
                runCatching {
                    val id = context.resources.getIdentifier(name, "raw", context.packageName)
                    val bytes = context.resources.openRawResource(id).use { it.readBytes() }
                    val cert = factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
                    store.setCertificateEntry("bundled-$index-$name", cert)
                }
            }
            return store
        }
    }
}
