package co.samidev.kilometrix.obd.ble

import java.util.Locale
import java.util.UUID

internal object BleUuids {
    val CLIENT_CHARACTERISTIC_CONFIGURATION: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

class ElmResponseBuffer(
    private val maxLength: Int = DEFAULT_MAX_LENGTH
) {
    private val contents = StringBuilder()

    fun append(fragment: String): String? {
        val promptIndex = fragment.indexOf(PROMPT)
        val fragmentToAppend = if (promptIndex >= 0) fragment.substring(0, promptIndex) else fragment

        if (contents.length + fragmentToAppend.length > maxLength) {
            reset()
            return null
        }
        contents.append(fragmentToAppend)

        if (promptIndex < 0) return null
        return contents.toString().trim().also { reset() }
    }

    fun reset() {
        contents.setLength(0)
    }

    private companion object {
        const val PROMPT = '>'
        const val DEFAULT_MAX_LENGTH = 2048
    }
}

object ObdDistanceParser {

    fun parseOdometerA6(raw: String): Double? {
        val clean = raw.replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .uppercase(Locale.ROOT)
        val idx = clean.indexOf("41A6")
        if (idx == -1 || clean.length < idx + 12) return null

        return try {
            val a = clean.substring(idx + 4, idx + 6).toLong(16)
            val b = clean.substring(idx + 6, idx + 8).toLong(16)
            val c = clean.substring(idx + 8, idx + 10).toLong(16)
            val d = clean.substring(idx + 10, idx + 12).toLong(16)

            val totalTenths = (a shl 24) or (b shl 16) or (c shl 8) or d
            (totalTenths / 10.0).takeIf { it.isFinite() && it >= 0.0 }
        } catch (_: Exception) {
            null
        }
    }

    fun parseDistance31(raw: String): Double? {
        val clean = raw.replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .uppercase(Locale.ROOT)
        val idx = clean.indexOf("4131")
        if (idx == -1 || clean.length < idx + 8) return null

        return try {
            val a = clean.substring(idx + 4, idx + 6).toInt(16)
            val b = clean.substring(idx + 6, idx + 8).toInt(16)
            ((a * 256) + b).toDouble().takeIf { it.isFinite() && it >= 0.0 }
        } catch (_: Exception) {
            null
        }
    }
}

object ElmResponseParser {

    fun parseElmVersion(response: String): String? = response
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.contains("ELM", ignoreCase = true) && it != ">" }
        ?.removeSuffix(">")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    fun parseProtocolDescription(response: String): String? = response
        .lineSequence()
        .map(String::trim)
        .firstOrNull { line ->
            line.contains("ISO 15765-4", ignoreCase = true) ||
                line.contains("CAN", ignoreCase = true)
        }
        ?.removeSuffix(">")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    fun isMqbCan11Bit500K(protocolNumber: String): Boolean =
        protocolNumber.trim().uppercase() in setOf("6", "A6")

    fun parseRpm(response: String): Int? {
        val normalized = response.uppercase().filter(Char::isLetterOrDigit)
        val responseIndex = normalized.indexOf(RPM_RESPONSE_PREFIX)
        if (responseIndex == -1) return null

        val dataStart = responseIndex + RPM_RESPONSE_PREFIX.length
        val rpmBytes = normalized.substringOrNull(dataStart, dataStart + RPM_DATA_LENGTH) ?: return null
        if (rpmBytes.any { it.digitToIntOrNull(16) == null }) return null

        val value = rpmBytes.toIntOrNull(16) ?: return null
        return value / 4
    }

    fun parseSpeedKph(response: String): Int? {
        val normalized = response.uppercase().filter(Char::isLetterOrDigit)
        val responseIndex = normalized.indexOf(SPEED_RESPONSE_PREFIX)
        if (responseIndex == -1) return null

        val dataStart = responseIndex + SPEED_RESPONSE_PREFIX.length
        val speedByte = normalized.substringOrNull(dataStart, dataStart + SPEED_DATA_LENGTH) ?: return null
        if (speedByte.any { it.digitToIntOrNull(16) == null }) return null

        return speedByte.toIntOrNull(16)
    }

    fun parseMapKpa(response: String): Int? = parseMode01Byte(response, MAP_RESPONSE_PREFIX)

    fun parseIntakeAirTemperatureCelsius(response: String): Int? =
        parseMode01Byte(response, INTAKE_AIR_TEMPERATURE_RESPONSE_PREFIX)?.minus(40)

    /**
     * Parsea MAF (PID 0110 -> respuesta 41 10 A B) en gramos/segundo: ((256*A + B) / 100.0).
     */
    fun parseMafGramsPerSecond(response: String): Double? {
        val normalized = response.uppercase().filter(Char::isLetterOrDigit)
        val responseIndex = normalized.indexOf(MAF_RESPONSE_PREFIX)
        if (responseIndex == -1) return null

        val dataStart = responseIndex + MAF_RESPONSE_PREFIX.length
        val mafBytes = normalized.substringOrNull(dataStart, dataStart + MAF_DATA_LENGTH) ?: return null
        if (mafBytes.any { it.digitToIntOrNull(16) == null }) return null

        val rawValue = mafBytes.toIntOrNull(16) ?: return null
        return (rawValue / 100.0).takeIf { it.isFinite() && it >= 0.0 }
    }

    /**
     * Parsea nivel de tanque de combustible (PID 012F -> respuesta 41 2F A) en porcentaje (0..100%).
     * Si el byte es 0xFF (255 = N/A en clusters VAG sin cableado a ECM), retorna null.
     */
    fun parseFuelLevelPct(response: String): Double? {
        val rawByte = parseMode01Byte(response, FUEL_LEVEL_RESPONSE_PREFIX) ?: return null
        if (rawByte !in 0..254) return null
        return ((rawByte * 100.0) / 255.0).takeIf { it.isFinite() && it in 0.0..100.0 }
    }

    fun parseOdometerKm(response: String): Double? =
        ObdDistanceParser.parseOdometerA6(response)

    fun parseDistanceSinceCodesClearedKm(response: String): Double? =
        ObdDistanceParser.parseDistance31(response)

    private fun parseMode01Byte(response: String, prefix: String): Int? {
        val normalized = response.uppercase().filter(Char::isLetterOrDigit)
        val responseIndex = normalized.indexOf(prefix)
        if (responseIndex == -1) return null

        val dataStart = responseIndex + prefix.length
        val dataByte = normalized.substringOrNull(dataStart, dataStart + HEX_CHARS_PER_BYTE)
            ?: return null
        if (dataByte.any { it.digitToIntOrNull(16) == null }) return null

        return dataByte.toIntOrNull(16)
    }

    fun supportsPid(response: String, pid: Int): Boolean =
        supportsPidInBlock(response, blockBasePid = 0x00, pid = pid)

    /**
     * Evalúa si un PID está soportado en cualquiera de los bloques estándar (0100, 0120, 0140).
     */
    fun supportsPidInBlock(response: String, blockBasePid: Int, pid: Int): Boolean {
        val firstPid = blockBasePid + 1
        val lastPid = blockBasePid + 0x20
        if (pid !in firstPid..lastPid) return false

        val prefix = "41" + blockBasePid.toString(16).uppercase().padStart(2, '0')
        val normalized = response.uppercase().filter(Char::isLetterOrDigit)
        val responseIndex = normalized.indexOf(prefix)
        if (responseIndex == -1) return false

        val bitmapStart = responseIndex + prefix.length
        val bitmap = normalized.substringOrNull(bitmapStart, bitmapStart + SUPPORT_BITMAP_LENGTH)
            ?: return false
        if (bitmap.any { it.digitToIntOrNull(16) == null }) return false

        val pidIndex = pid - firstPid
        val byteStart = pidIndex / BITS_PER_BYTE * HEX_CHARS_PER_BYTE
        val byteValue = bitmap.substring(byteStart, byteStart + HEX_CHARS_PER_BYTE).toInt(16)
        val bitMask = 1 shl (BITS_PER_BYTE - 1 - pidIndex % BITS_PER_BYTE)
        return (byteValue and bitMask) != 0
    }

    fun parseMode09Ascii(response: String, infoType: Int): String? {
        if (infoType !in 0x01..0x0A) return null
        val normalized = normalizeMode09Hex(response)
        val prefix = "49" + infoType.toString(16).uppercase().padStart(2, '0')
        val responseIndex = normalized.indexOf(prefix)
        if (responseIndex == -1) return null

        val payloadStart = responseIndex + prefix.length
        val payload = normalized.substringOrNull(payloadStart, normalized.length) ?: return null
        if (payload.length < HEX_CHARS_PER_BYTE * 2 || payload.length % HEX_CHARS_PER_BYTE != 0) return null

        val recordCount = payload.substring(0, HEX_CHARS_PER_BYTE).toIntOrNull(16) ?: return null
        if (recordCount == 0) return null
        val asciiHex = payload.substring(HEX_CHARS_PER_BYTE)
        val ascii = asciiHex.chunked(HEX_CHARS_PER_BYTE)
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
        return String(ascii, java.nio.charset.StandardCharsets.US_ASCII)
            .trim { it.isWhitespace() || it == '\u0000' }
            .takeIf(String::isNotBlank)
    }

    fun parseVin(response: String): String? {
        val vin = parseMode09Ascii(response, VIN_INFO_TYPE)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?: return null
        return vin.takeIf { value ->
            value.length == VIN_LENGTH && value.all { character ->
                character in 'A'..'Z' || character in '0'..'9'
            }
        }
    }

    private fun normalizeMode09Hex(response: String): String = response
        .lineSequence()
        .flatMap { line ->
            ISO_TP_FRAME_INDEX.replace(line.replace('>', ' '), " ")
                .split(WHITESPACE)
                .asSequence()
        }
        .filter { token ->
            token.length >= HEX_CHARS_PER_BYTE && token.length % HEX_CHARS_PER_BYTE == 0 &&
                token.all { character -> character.digitToIntOrNull(16) != null }
        }
        .joinToString(separator = "")
        .uppercase(Locale.ROOT)

    private fun String.substringOrNull(startIndex: Int, endIndex: Int): String? =
        if (startIndex < 0 || endIndex > length || startIndex >= endIndex) {
            null
        } else {
            substring(startIndex, endIndex)
        }

    private const val RPM_RESPONSE_PREFIX = "410C"
    private const val RPM_DATA_LENGTH = 4
    private const val SPEED_RESPONSE_PREFIX = "410D"
    private const val SPEED_DATA_LENGTH = 2
    private const val MAP_RESPONSE_PREFIX = "410B"
    private const val INTAKE_AIR_TEMPERATURE_RESPONSE_PREFIX = "410F"
    private const val MAF_RESPONSE_PREFIX = "4110"
    private const val MAF_DATA_LENGTH = 4
    private const val FUEL_LEVEL_RESPONSE_PREFIX = "412F"
    private const val SUPPORT_BITMAP_LENGTH = 8
    private const val BITS_PER_BYTE = 8
    private const val HEX_CHARS_PER_BYTE = 2
    private const val VIN_INFO_TYPE = 0x02
    private const val VIN_LENGTH = 17
    private val ISO_TP_FRAME_INDEX = Regex("(?<![0-9A-Fa-f])\\d+\\s*:\\s*")
    private val WHITESPACE = Regex("\\s+")
}
