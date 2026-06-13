package me.rerere.rikkahub.utils

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * PNG tEXt 文本块读写工具（纯字节操作，便于测试）。
 *
 * SillyTavern 角色卡将角色数据以 base64 存入关键字为 "chara"(V2) / "ccv3"(V3)
 * 的 tEXt 块。本工具用于导出时写入、以及测试时回读。
 *
 * PNG 结构：8 字节签名 + 若干 chunk。每个 chunk = [4字节长度][4字节类型][数据][4字节CRC]。
 * tEXt 数据 = 关键字(Latin-1) + 0x00 + 文本(Latin-1)。CRC 覆盖 类型+数据。
 */
object PngTextChunk {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /** 校验字节数组是否为 PNG */
    fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }

    /**
     * 在 IEND 之前插入一个 tEXt 块，返回新的 PNG 字节数组。
     * 若已存在同名关键字的 tEXt 块，会先移除旧块。
     */
    fun addTextChunk(pngBytes: ByteArray, keyword: String, text: String): ByteArray {
        require(isPng(pngBytes)) { "Not a valid PNG" }
        val withoutOld = removeTextChunk(pngBytes, keyword)
        val iendIndex = findChunkOffset(withoutOld, "IEND")
            ?: error("PNG missing IEND chunk")

        val out = ByteArrayOutputStream(withoutOld.size + text.length + 64)
        out.write(withoutOld, 0, iendIndex)            // 直到 IEND 之前的所有内容
        out.write(buildTextChunk(keyword, text))       // 插入 tEXt
        out.write(withoutOld, iendIndex, withoutOld.size - iendIndex) // IEND 及之后
        return out.toByteArray()
    }

    /** 读取所有 tEXt 块为 关键字→文本 映射 */
    fun readTextChunks(pngBytes: ByteArray): Map<String, String> {
        if (!isPng(pngBytes)) return emptyMap()
        val result = LinkedHashMap<String, String>()
        var offset = 8
        while (offset + 8 <= pngBytes.size) {
            val length = readInt(pngBytes, offset)
            val type = String(pngBytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            if (length < 0 || dataStart + length > pngBytes.size) break
            if (type == "tEXt") {
                val data = pngBytes.copyOfRange(dataStart, dataStart + length)
                val sep = data.indexOf(0)
                if (sep >= 0) {
                    val keyword = String(data, 0, sep, Charsets.ISO_8859_1)
                    val value = String(data, sep + 1, data.size - sep - 1, Charsets.ISO_8859_1)
                    result[keyword] = value
                }
            }
            if (type == "IEND") break
            offset = dataStart + length + 4 // 跳过数据 + CRC
        }
        return result
    }

    private fun removeTextChunk(pngBytes: ByteArray, keyword: String): ByteArray {
        var offset = 8
        val out = ByteArrayOutputStream(pngBytes.size)
        out.write(pngBytes, 0, 8)
        while (offset + 8 <= pngBytes.size) {
            val length = readInt(pngBytes, offset)
            val type = String(pngBytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            if (length < 0 || dataStart + length > pngBytes.size) {
                // 异常，原样追加剩余字节
                out.write(pngBytes, offset, pngBytes.size - offset)
                return out.toByteArray()
            }
            val chunkEnd = dataStart + length + 4
            val isMatchingText = type == "tEXt" && run {
                val data = pngBytes.copyOfRange(dataStart, dataStart + length)
                val sep = data.indexOf(0)
                sep >= 0 && String(data, 0, sep, Charsets.ISO_8859_1) == keyword
            }
            if (!isMatchingText) {
                out.write(pngBytes, offset, chunkEnd - offset)
            }
            if (type == "IEND") break
            offset = chunkEnd
        }
        return out.toByteArray()
    }

    private fun buildTextChunk(keyword: String, text: String): ByteArray {
        val keywordBytes = keyword.toByteArray(Charsets.ISO_8859_1)
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        val data = ByteArrayOutputStream()
        data.write(keywordBytes)
        data.write(0)
        data.write(textBytes)
        val dataBytes = data.toByteArray()

        val typeBytes = "tEXt".toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(dataBytes)
        }.value

        val out = ByteArrayOutputStream(dataBytes.size + 12)
        out.write(intToBytes(dataBytes.size))
        out.write(typeBytes)
        out.write(dataBytes)
        out.write(intToBytes(crc.toInt()))
        return out.toByteArray()
    }

    private fun findChunkOffset(pngBytes: ByteArray, chunkType: String): Int? {
        var offset = 8
        while (offset + 8 <= pngBytes.size) {
            val length = readInt(pngBytes, offset)
            val type = String(pngBytes, offset + 4, 4, Charsets.US_ASCII)
            if (length < 0) return null
            if (type == chunkType) return offset
            offset += 8 + length + 4
        }
        return null
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )
}
