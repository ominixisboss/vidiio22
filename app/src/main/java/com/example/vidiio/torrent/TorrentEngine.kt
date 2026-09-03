package com.example.vidiio.torrent

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.*
import com.frostwire.jlibtorrent.swig.settings_pack
import com.frostwire.jlibtorrent.swig.alert_category_t

class TorrentEngine(private val context: Context) {
    private var sessionManager: SessionManager? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null
    var isAvailable: Boolean = false
        private set

    init {
        try {
            acquireMulticastLock()
            sessionManager = SessionManager()
            val sp = SettingsPack()
            
            // Basic settings
            sp.setInteger(settings_pack.int_types.alert_mask.swigValue(), alert_category_t.all().to_int())
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
            
            // V3 optimizations (faster peer discovery, etc.)
            sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 500)
            sp.setInteger(settings_pack.int_types.active_dht_limit.swigValue(), 400)
            sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), 200)
            sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 200)
            sp.setInteger(settings_pack.int_types.dht_announce_interval.swigValue(), 30)
            sp.setInteger(settings_pack.int_types.inactivity_timeout.swigValue(), 30)
            sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), 500)
            
            // Streaming and connection optimizations
            sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)
            sp.setInteger(settings_pack.int_types.mixed_mode_algorithm.swigValue(), settings_pack.bandwidth_mixed_algo_t.peer_proportional.swigValue())
            
            // Enable all protocols
            sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true)
            
            sessionManager?.start(SessionParams(sp))
            isAvailable = true
            Log.i("TorrentEngine", "Torrent engine initialized successfully.")
        } catch (t: Throwable) {
            Log.e("TorrentEngine", "Failed to initialize libtorrent: ${t.message}", t)
            isAvailable = false
        }
    }

    fun getSession(): SessionManager? = sessionManager

    fun stop() {
        releaseMulticastLock()
        sessionManager?.stop()
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            multicastLock = wifiManager.createMulticastLock("VidiioMulticastLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.e("TorrentEngine", "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.release()
        } catch (e: Exception) {}
    }
}
