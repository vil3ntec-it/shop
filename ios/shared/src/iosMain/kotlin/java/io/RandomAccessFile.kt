package java.io

/*
 *  shimِ iOS ِ `RandomAccessFile` («r» و «rw»): فایل یک‌جا در حافظه؛ `seek`/`read`/`write`/`setLength` همان
 *  JDK. در «rw» تغییرها در `close` یک‌جا و همگام (`fsync`) نوشته می‌شوند — `fd.sync()` ِ پیش از بستن چیزی ندارد.
 */
class RandomAccessFile(private val file: File, mode: String) : Closeable {
  private val writable = mode.startsWith("rw")
  private var bytes: ByteArray
  private var size: Int
  private var pos = 0L
  private var dirty = false
  val fd: FileDescriptor = FileDescriptor()

  init {
    if (mode != "r" && !writable) throw IllegalArgumentException("Illegal mode \"$mode\"")
    bytes = if (file.exists()) file.readBytes() else if (writable) ByteArray(0) else throw FileNotFoundException("${file.path} (No such file or directory)")
    size = bytes.size
  }

  constructor(path: String, mode: String) : this(File(path), mode)

  fun length(): Long = size.toLong()
  fun seek(p: Long) { if (p < 0) throw IOException("Negative seek offset"); pos = p }
  fun getFilePointer(): Long = pos
  fun read(): Int = if (pos < size) bytes[pos++.toInt()].toInt() and 0xFF else -1

  fun read(b: ByteArray, off: Int = 0, len: Int = b.size): Int {
    if (pos >= size) return -1
    val n = minOf(len.toLong(), size - pos).toInt()
    bytes.copyInto(b, off, pos.toInt(), pos.toInt() + n)
    pos += n
    return n
  }

  fun readFully(b: ByteArray) {
    if (read(b, 0, b.size) != b.size) throw EOFException()
  }

  private fun ensure(cap: Int) {
    if (cap > bytes.size) bytes = bytes.copyOf(maxOf(cap, bytes.size * 2))
  }

  fun setLength(newLength: Long) {
    check(writable) { "read-only" }
    val n = newLength.toInt()
    ensure(n)
    if (n > size) bytes.fill(0, size, n)
    size = n
    if (pos > size) pos = size.toLong()
    dirty = true
  }

  fun write(b: ByteArray, off: Int = 0, len: Int = b.size) {
    if (!writable) throw IOException("Bad file descriptor")
    val at = pos.toInt()
    ensure(at + len)
    if (at > size) bytes.fill(0, size, at)
    b.copyInto(bytes, at, off, off + len)
    pos += len
    if (pos > size) size = pos.toInt()
    dirty = true
  }

  fun write(b: Int) = write(byteArrayOf(b.toByte()))

  override fun close() {
    if (dirty) { PosixFile.write(file.path, bytes.copyOf(size), append = false); dirty = false }
  }
}

open class EOFException(message: String? = null) : IOException(message)
