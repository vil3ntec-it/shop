package java.util.zip

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/*
 *  shimِ iOS ِ `ZipInputStream` (خواندنِ فایلِ xlsx/zip ِ ورودِ کالا) روی zlib ِ خودِ iOS. فقط خواندن:
 *  «ذخیره‌شده» (۰) و «فشرده» (۸)، با یا بی «توصیفِ داده» ِ پس از بدنه. ورودی یک‌جا در حافظه است.
 *  خرابی ⇐ ZipException (مثلِ JDK)، نه بدنهٔ ناقص.
 */
open class ZipException(message: String?) : IOException(message)

class ZipEntry(val name: String) {
  var size: Long = -1
  var compressedSize: Long = -1
  var method: Int = -1
  val isDirectory: Boolean get() = name.endsWith("/")
  override fun toString(): String = name
}

class ZipInputStream(input: InputStream) : InputStream() {
  private val buf: ByteArray = input.readBytes()
  private var pos = 0
  private var current: ByteArray? = null
  private var cur = 0

  private fun u16(at: Int): Int = (buf[at].toInt() and 0xFF) or ((buf[at + 1].toInt() and 0xFF) shl 8)
  private fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

  val nextEntry: ZipEntry? get() = getNextEntry()

  fun getNextEntry(): ZipEntry? {
    current = null
    if (pos + 30 > buf.size || u32(pos) != 0x04034b50L) return null
    val flags = u16(pos + 6)
    val method = u16(pos + 8)
    var comp = u32(pos + 18)
    val nameLen = u16(pos + 26)
    val extraLen = u16(pos + 28)
    val nameAt = pos + 30
    if (nameAt + nameLen + extraLen > buf.size) throw ZipException("invalid LOC header")
    val name = buf.decodeToString(nameAt, nameAt + nameLen)
    val dataAt = nameAt + nameLen + extraLen
    val described = flags and 0x08 != 0
    val (data, used) = when (method) {
      0 -> {
        if (described) throw ZipException("STORED entry with data descriptor")
        if (dataAt + comp > buf.size) throw ZipException("truncated entry")
        buf.copyOfRange(dataAt, dataAt + comp.toInt()) to comp.toInt()
      }
      8 -> inflateRaw(dataAt, if (described) buf.size - dataAt else comp.toInt())
      else -> throw ZipException("invalid compression method $method")
    }
    comp = used.toLong()
    var next = dataAt + used
    if (described) {
      if (next + 4 <= buf.size && u32(next) == 0x08074b50L) next += 4
      next += 12
    }
    pos = next
    current = data
    cur = 0
    return ZipEntry(name).also { it.size = data.size.toLong(); it.compressedSize = comp; it.method = method }
  }

  fun closeEntry() { current = null }

  override fun read(): Int {
    val c = current ?: return -1
    return if (cur < c.size) c[cur++].toInt() and 0xFF else -1
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    val c = current ?: return -1
    if (len == 0) return 0
    if (cur >= c.size) return -1
    val n = minOf(len, c.size - cur)
    c.copyInto(b, off, cur, cur + n)
    cur += n
    return n
  }

  /** deflate ِ خام (بی سرآیندِ zlib) ⇐ [بایت‌ها، شمارِ بایتِ فشردهٔ مصرف‌شده] */
  private fun inflateRaw(at: Int, maxLen: Int): Pair<ByteArray, Int> = memScoped {
    val z = alloc<z_stream>()
    if (inflateInit2_(z.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().convert()) != Z_OK) throw ZipException("inflateInit")
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    try {
      buf.usePinned { inPin ->
        z.next_in = inPin.addressOf(at).reinterpret()
        z.avail_in = maxLen.convert()
        while (true) {
          val rc = chunk.usePinned { outPin ->
            z.next_out = outPin.addressOf(0).reinterpret()
            z.avail_out = chunk.size.convert()
            inflate(z.ptr, Z_NO_FLUSH)
          }
          val produced = chunk.size - z.avail_out.toInt()
          out.write(chunk, 0, produced)
          if (rc == Z_STREAM_END) break
          if (rc != Z_OK && !(rc == Z_BUF_ERROR && produced > 0)) throw ZipException("invalid deflate data ($rc)")
        }
      }
      out.toByteArray() to (maxLen - z.avail_in.toInt())
    } finally {
      inflateEnd(z.ptr)
    }
  }
}

/** `CRC32` ِ JDK روی `crc32` ِ zlib */
class CRC32 {
  private var crc: ULong = 0u
  fun update(b: ByteArray) = update(b, 0, b.size)
  fun update(b: ByteArray, off: Int, len: Int) {
    if (len == 0) return
    crc = b.usePinned { platform.zlib.crc32(crc, it.addressOf(off).reinterpret(), len.convert()) }
  }
  fun update(b: Int) = update(byteArrayOf(b.toByte()))
  val value: Long get() = crc.toLong()
  fun reset() { crc = 0u }
}

/*
 *  `ZipOutputStream` ِ JDK (خروجیِ xlsx): هر ورودی در حافظه جمع و در `closeEntry` با deflate ِ خامِ zlib
 *  و سرآیندِ محلیِ کامل (بی «توصیفِ داده») نوشته می‌شود؛ `close` فهرستِ مرکزی را می‌نویسد.
 *  بایت‌به‌بایت مثلِ JDK نیست (او توصیفِ داده می‌نویسد)، ولی همان zip ِ استاندارد است.
 */
class ZipOutputStream(private val out: java.io.OutputStream) : java.io.OutputStream() {
  private class Done(val name: ByteArray, val crc: Long, val comp: Int, val size: Int, val offset: Int)

  private var entry: ZipEntry? = null
  private val data = ByteArrayOutputStream()
  private val done = ArrayList<Done>()
  private var written = 0
  private var finished = false

  fun putNextEntry(e: ZipEntry) {
    if (entry != null) closeEntry()
    entry = e
    data.reset()
  }

  override fun write(b: Int) { check(entry != null) { "no current ZIP entry" }; data.write(b) }

  override fun write(b: ByteArray, off: Int, len: Int) { check(entry != null) { "no current ZIP entry" }; data.write(b, off, len) }

  fun closeEntry() {
    val e = entry ?: return
    entry = null
    val raw = data.toByteArray()
    val crc = CRC32().also { it.update(raw) }.value
    val comp = deflateRaw(raw)
    val name = e.name.encodeToByteArray()
    val header = ByteArrayOutputStream()
    header.le32(0x04034b50); header.le16(20); header.le16(0x0800); header.le16(8); header.le16(0); header.le16(0x21)
    header.le32(crc); header.le32(comp.size.toLong()); header.le32(raw.size.toLong()); header.le16(name.size); header.le16(0)
    header.write(name, 0, name.size)
    val h = header.toByteArray()
    out.write(h, 0, h.size)
    out.write(comp, 0, comp.size)
    done += Done(name, crc, comp.size, raw.size, written)
    written += h.size + comp.size
  }

  fun finish() {
    if (finished) return
    closeEntry()
    finished = true
    val dir = ByteArrayOutputStream()
    for (d in done) {
      dir.le32(0x02014b50); dir.le16(20); dir.le16(20); dir.le16(0x0800); dir.le16(8); dir.le16(0); dir.le16(0x21)
      dir.le32(d.crc); dir.le32(d.comp.toLong()); dir.le32(d.size.toLong()); dir.le16(d.name.size); dir.le16(0); dir.le16(0)
      dir.le16(0); dir.le16(0); dir.le32(0); dir.le32(d.offset.toLong()); dir.write(d.name, 0, d.name.size)
    }
    val c = dir.toByteArray()
    val end = ByteArrayOutputStream()
    end.le32(0x06054b50); end.le16(0); end.le16(0); end.le16(done.size); end.le16(done.size)
    end.le32(c.size.toLong()); end.le32(written.toLong()); end.le16(0)
    val e = end.toByteArray()
    out.write(c, 0, c.size)
    out.write(e, 0, e.size)
    out.flush()
  }

  override fun flush() = out.flush()

  override fun close() {
    finish()
    out.close()
  }
}

private fun ByteArrayOutputStream.le16(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }
private fun ByteArrayOutputStream.le32(v: Long) { le16((v and 0xFFFF).toInt()); le16(((v shr 16) and 0xFFFF).toInt()) }
private fun ByteArrayOutputStream.le32(v: Int) = le32(v.toLong() and 0xFFFFFFFFL)

private fun deflateRaw(input: ByteArray): ByteArray = memScoped {
  val z = alloc<z_stream>()
  if (platform.zlib.deflateInit2_(z.ptr, platform.zlib.Z_DEFAULT_COMPRESSION, platform.zlib.Z_DEFLATED, -15, 8,
      platform.zlib.Z_DEFAULT_STRATEGY, ZLIB_VERSION, sizeOf<z_stream>().convert()) != Z_OK
  ) throw ZipException("deflateInit")
  val out = ByteArrayOutputStream()
  val chunk = ByteArray(64 * 1024)
  val src = if (input.isEmpty()) ByteArray(1) else input
  try {
    src.usePinned { inPin ->
      z.next_in = inPin.addressOf(0).reinterpret()
      z.avail_in = input.size.convert()
      while (true) {
        val rc = chunk.usePinned { outPin ->
          z.next_out = outPin.addressOf(0).reinterpret()
          z.avail_out = chunk.size.convert()
          platform.zlib.deflate(z.ptr, platform.zlib.Z_FINISH)
        }
        out.write(chunk, 0, chunk.size - z.avail_out.toInt())
        if (rc == Z_STREAM_END) break
        if (rc != Z_OK && rc != Z_BUF_ERROR) throw ZipException("deflate ($rc)")
      }
    }
    out.toByteArray()
  } finally {
    platform.zlib.deflateEnd(z.ptr)
  }
}
