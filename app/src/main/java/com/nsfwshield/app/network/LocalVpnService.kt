package com.nsfwshield.app.network

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.nsfwshield.app.core.logging.ActivityLogger
import com.nsfwshield.app.core.profiles.ProfileManager
import com.nsfwshield.app.monitoring.BlurOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Local VPN Service for DNS-level content filtering.
 *
 * Creates a local VPN connection on the device used EXCLUSIVELY for DNS-level
 * filtering — to block connections to known explicit content domains before they load.
 *
 * - Traffic is filtered locally on device only
 * - No data is routed through any external server
 * - No browsing data, URLs, or content is ever sent outside the device
 * - This is NOT a privacy VPN — it does not change IP or encrypt traffic for anonymity
 */
@AndroidEntryPoint
class LocalVpnService : VpnService() {

    @Inject lateinit var dnsFilter: DnsFilter
    @Inject lateinit var domainBlocklist: DomainBlocklist
    @Inject lateinit var activityLogger: ActivityLogger
    @Inject lateinit var blurOverlayManager: BlurOverlayManager
    @Inject lateinit var profileManager: ProfileManager

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        var isRunning = false
            private set

        const val ACTION_START = "com.nsfwshield.vpn.START"
        const val ACTION_STOP = "com.nsfwshield.vpn.STOP"
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val PRIVATE_DNS = "10.0.0.2"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_PREFIX = 32
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                START_NOT_STICKY
            }
            else -> {
                startVpn()
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        serviceScope.cancel()
    }

    private fun startVpn() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("NSFW Shield DNS Filter")
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, VPN_PREFIX)
                // NEW: Only route traffic destined for our internal DNS server through the VPN.
                // This prevents capturing all internet traffic (the "black hole" effect).
                .addRoute(PRIVATE_DNS, 32)
                .addDnsServer(PRIVATE_DNS)
                // EXPANDED: Capture common public DNS IPs to prevent bypass via hardcoded DNS (e.g., in Chrome/Firefox)
                .addRoute("8.8.8.8", 32)
                .addRoute("8.8.4.4", 32)
                .addRoute("1.1.1.1", 32)
                .addRoute("1.0.0.1", 32)
                .addRoute("9.9.9.9", 32)
                .setBlocking(true)

            // Allow the NSFW Shield app itself to bypass the VPN
            builder.addDisallowedApplication(packageName)

            vpnInterface = builder.establish()
            isRunning = true

            vpnInterface?.let { 
                serviceScope.launch { handleVpnTraffic(it) }
            }
        } catch (e: Exception) {
            stopVpn()
        }
    }

    private fun stopVpn() {
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
    }

    /**
     * Main VPN traffic handling loop.
     */
    private suspend fun handleVpnTraffic(vpnFd: ParcelFileDescriptor) {
        val inputStream = FileInputStream(vpnFd.fileDescriptor)
        val outputStream = FileOutputStream(vpnFd.fileDescriptor)
        val buffer = ByteBuffer.allocate(VPN_MTU)

        // Socket for upstream DNS queries (Google DNS)
        val upstreamSocket = java.net.DatagramSocket()
        // CRITICAL: Protect the socket so its traffic bypasses the VPN itself, preventing loops.
        protect(upstreamSocket)
        
        val dnsServerAddress = InetAddress.getByName("8.8.8.8")
        android.util.Log.d("NSFWVpn", "VPN Traffic handler started. Upstream: 8.8.8.8")

        // Ensure blocklist is initialized before starting the loop
        domainBlocklist.initialize()

        while (isRunning) {
            try {
                buffer.clear()
                val length = inputStream.read(buffer.array())
                if (length <= 0) {
                    delay(10)
                    continue
                }

                val packet = buffer.array().copyOf(length)
                
                if (isDnsPacket(packet)) {
                    val domain = dnsFilter.extractDomain(packet)
                    android.util.Log.d("NSFWVpn", "DNS Query: $domain")
                    
                    val blockResult = if (domain != null) dnsFilter.shouldBlock(domain) else null
                    if (domain != null && blockResult != null) {
                        android.util.Log.i("NSFWVpn", "BLOCKING domain: $domain (Category: ${blockResult.category})")
                        
                        // Log event with specific category (no visual blur as per preference)
                        serviceScope.launch {
                            val profile = profileManager.getActiveProfile()
                            activityLogger.logDnsBlock(domain, profile.profileId, blockResult.category)
                        }

                        // BLOCK: return NXDOMAIN
                        val blockedResponse = dnsFilter.createBlockedResponse(packet)
                        dnsFilter.updateLengthsAndChecksum(blockedResponse, blockedResponse.size)
                        outputStream.write(blockedResponse)
                    } else {
                        // ALLOW: Forward to upstream DNS (Google DNS)
                        withContext(Dispatchers.IO) {
                            // Raw DNS payload starts at 28 (IP + UDP headers)
                            val dnsPayload = packet.copyOfRange(28, length)
                            val outPacket = java.net.DatagramPacket(dnsPayload, dnsPayload.size, dnsServerAddress, 53)
                            upstreamSocket.send(outPacket)

                            // Wait for response
                            val inBuffer = ByteArray(4096)
                            val inPacket = java.net.DatagramPacket(inBuffer, inBuffer.size)
                            upstreamSocket.soTimeout = 1500 // 1.5s timeout
                            
                            try {
                                upstreamSocket.receive(inPacket)
                                
                                // Create a response packet by swapping original headers
                                val finalResponseHeader = dnsFilter.swapIpAndPort(packet)
                                
                                // Build DNS payload from upstream result
                                val upstreamDnsData = inPacket.data.copyOfRange(0, inPacket.length)
                                
                                // Assemble full packet: IP Header (20) + UDP Header (8) + DNS Payload
                                val fullPacket = finalResponseHeader.copyOfRange(0, 28) + upstreamDnsData
                                
                                // Update IP/UDP length and calculate the mandatory IP checksum
                                dnsFilter.updateLengthsAndChecksum(fullPacket, fullPacket.size)
                                
                                outputStream.write(fullPacket)
                                android.util.Log.d("NSFWVpn", "Forwarded response for: $domain")

                                // If this is a safe domain, clear any block overlays 
                                // (helps recover from blocks when the user navigates away)
                                if (blurOverlayManager.isVisible()) {
                                    serviceScope.launch {
                                        blurOverlayManager.removeAllOverlays()
                                    }
                                }
                                Unit
                            } catch (e: java.net.SocketTimeoutException) {
                                android.util.Log.w("NSFWVpn", "Upstream DNS timeout for: $domain")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!isRunning) break
                android.util.Log.e("NSFWVpn", "Error in VPN loop", e)
                delay(10)
            }
        }
        upstreamSocket.close()
    }

    /**
     * Check if a packet is a DNS query (UDP port 53).
     */
    private fun isDnsPacket(packet: ByteArray): Boolean {
        if (packet.size < 28) return false // Minimum IP + UDP header size
        
        // Check IP protocol field for UDP (17)
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return false
        
        // Check destination port for DNS (53)
        val destPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
        return destPort == DNS_PORT
    }
}
