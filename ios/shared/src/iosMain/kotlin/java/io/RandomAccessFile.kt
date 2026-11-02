package java.io

/** shimِ iOS ِ `RandomAccessFile` ِ فقط-خواندنی («r»): فایل یک‌جا در حافظه؛ `seek`/`read`/`length` همان JDK */
class RandomAccessFile(file: File, mode: String) : Closeable {
  private val bytes: ByteArray
  private var pos = 0L

  init {
    if (mode != "r") throw IllegalArgumentException("iOS: فقط حالتِ «r»")
    bytes = file.readBytes()
  }

  constructor(path: String, mode: String) : this(File(path), mode)

  fun length(): Long = bytes.size.toLong()
  fun seek(p: Long) { if (p < 0) throw IOException("Negative seek offset"); pos = p }
  fun getFilePointer(): Long = pos
  fun read(): Int = if (pos < bytes.size) bytes[pos++.toInt()].toInt() and 0xFF else -1

  fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
    if (pos >= bytes.size) return -1
    val n = minOf(len.toLong(), bytes.size - pos).toInt()
    bytes.copyInto(b, off, pos.toInt(), pos.toInt() + n)
    pos += n
    return n
  }

  fun readFully(b: ByteArray) {
    if (read(b, 0, b.size) != b.size) throw EOFException()
  }

  override fun close() {}
}

open class EOFException(message: String? = null) : IOException(message)
