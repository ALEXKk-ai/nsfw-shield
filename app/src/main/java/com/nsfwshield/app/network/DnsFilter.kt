package com.nsfwshield.app.network

import com.nsfwshield.app.data.BlockedDomainDao

import javax.inject.Inject
import javax.inject.Singleton

/**
 * DNS packet parser and filter.
 * Extracts domain names from DNS queries and checks against the blocklist.
 */
@Singleton
class DnsFilter @Inject constructor(
    private val blockedDomainDao: BlockedDomainDao,
    private val domainBlocklist: DomainBlocklist
) {
    companion object {
        private const val DNS_HEADER_SIZE = 12
        private const val DNS_TYPE_A = 1
        private const val DNS_CLASS_IN = 1
        private const val RCODE_NXDOMAIN = 3
    }

    /**
     * Extract the queried domain name from a raw DNS packet.
     * The DNS query starts after the IP + UDP headers (offset 28 for IPv4).
     */
    fun extractDomain(packet: ByteArray): String? {
        try {
            // Skip IP header (20 bytes) + UDP header (8 bytes) = 28 bytes
            val dnsStart = 28
            if (packet.size < dnsStart + DNS_HEADER_SIZE) return null

            // Parse DNS question section
            var offset = dnsStart + DNS_HEADER_SIZE
            val domainParts = mutableListOf<String>()

            while (offset < packet.size) {
                val labelLength = packet[offset].toInt() and 0xFF
                if (labelLength == 0) break

                if (offset + 1 + labelLength > packet.size) break

                val label = String(packet, offset + 1, labelLength)
                domainParts.add(label)
                offset += labelLength + 1
            }

            return if (domainParts.isNotEmpty()) {
                domainParts.joinToString(".").lowercase()
            } else null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Result of a block check, containing metadata for logging.
     */
    data class BlockResult(
        val category: String,
        val isCustom: Boolean
    )

    /**
     * Check if a domain should be blocked.
     * Checks both the in-memory blocklist and the Room database.
     */
    suspend fun shouldBlock(domain: String): BlockResult? {
        // First check in-memory blocklist (fast path)
        if (domainBlocklist.isBlocked(domain)) {
            val category = domainBlocklist.getBuiltinCategory(domain) ?: "adult"
            return BlockResult(category = category, isCustom = false)
        }

        // Check against database (for custom entries)
        val customCategory = blockedDomainDao.getDomainCategory(domain)
        if (customCategory != null) {
            return BlockResult(category = customCategory, isCustom = true)
        }

        return null
    }

    /**
     * Create an NXDOMAIN DNS response for a blocked domain.
     */
    fun createBlockedResponse(originalPacket: ByteArray): ByteArray {
        val response = swapIpAndPort(originalPacket)
        val dnsStart = 28
        
        if (response.size >= dnsStart + 4) {
            // Set DNS flags: QR=1 (response), RCODE=3 (NXDOMAIN)
            response[dnsStart + 2] = (0x81).toByte() // QR=1, Opcode=0, AA=0, TC=0, RD=1
            response[dnsStart + 3] = (0x83).toByte() // RA=1, Z=0, RCODE=3 (NXDOMAIN)
        }
        
        return response
    }

    /**
     * Swaps source and destination IPs and Ports in an IPv4/UDP packet.
     */
    fun swapIpAndPort(packet: ByteArray): ByteArray {
        val response = packet.copyOf()
        if (response.size < 28) return response

        // Swap source and destination IP addresses (Offsets 12-15 and 16-19)
        for (i in 0..3) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }

        // Swap source and destination ports (Offsets 20-21 and 22-23)
        val srcPort0 = response[20]
        val srcPort1 = response[21]
        response[20] = response[22]
        response[21] = response[23]
        response[22] = srcPort0
        response[23] = srcPort1
        
        return response
    }

    /**
     * Updates IP and UDP length fields and calculates the mandatory IP checksum.
     */
    fun updateLengthsAndChecksum(packet: ByteArray, totalLength: Int) {
        if (packet.size < 20) return
        
        // IP Total Length (Bytes 2-3)
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        
        // Reset Checksum fields before calculation (Bytes 10-11)
        packet[10] = 0
        packet[11] = 0
        
        // Calculate and set IP Checksum
        val checksum = calculateChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
        
        // UDP Length (Bytes 24-25)
        if (packet.size >= 28) {
            val udpLength = totalLength - 20
            packet[24] = (udpLength shr 8).toByte()
            packet[25] = (udpLength and 0xFF).toByte()
            
            // Clear UDP checksum (Bytes 26-27) - OS will ignore if 0
            packet[26] = 0
            packet[27] = 0
        }
    }

    /**
     * Calculates the 16-bit one's complement sum (Internet Checksum).
     */
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        
        while (i < offset + length - 1) {
            val high = (data[i].toInt() and 0xFF) shl 8
            val low = data[i + 1].toInt() and 0xFF
            sum += (high or low)
            i += 2
        }
        
        // Handle odd length
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        
        // Fold 32-bit sum to 16 bits
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        
        return (sum.inv() and 0xFFFF)
    }

    /**
     * Check if a domain matches any blocked pattern (supports wildcard matching).
     */
    fun matchesDomainPattern(domain: String, pattern: String): Boolean {
        if (pattern.startsWith("*.")) {
            val suffix = pattern.removePrefix("*")
            return domain.endsWith(suffix) || domain == pattern.removePrefix("*.")
        }
        return domain == pattern
    }
}
