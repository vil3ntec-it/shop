package java.io

import ir.vil3ntec.tohid.ios.jvm.Charset

/*
 *  shimِ iOS ِ جریان‌های `java.io` — همان قراردادِ JDK: `read` در پایان ‎-1‎، `read(buf)` شمارِ
 *  خوانده‌شده، `close` چندباره بی‌خطر. فقط بایت (بی بافرِ سیستمی): فایل‌ها کوچک‌اند و یک‌جا خوانده
 *  و نوشته می‌شوند (‎File.kt‎ ⇐ NSData). `use { }` همان `kotlin.use` ِ `AutoCloseable` است.
 */
interface Closeable : AutoCloseable {
  override fun close()
}

interface Flushable {
  fun flush()
}

abstract class InputStream : Closeable {
  abstract fun read(): Int

  open fun read(b: ByteArray): Int = read(b, 0, b.size)

  open fun read(b: ByteArray, off: Int, len: Int): Int {
    if (len == 0) return 0
    var n = 0
    while (n < len) {
      val c = read()
      if (c < 0) break
      b[off + n] = c.toByte()
      n++
    }
    return if (n == 0) -1 else n
  }

  open fun readBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    copyTo(out)
    return out.toByteArray()
  }

  open fun readAllBytes(): ByteArray = readBytes()

  open fun available(): Int = 0

  open fun skip(n: Long): Long {
    var k = 0L
    while (k < n && read() >= 0) k++
    return k
  }

  override fun close() {}

  fun copyTo(out: OutputStream, bufferSize: Int = 8 * 1024): Long {
    val buf = ByteArray(bufferSize)
    var total = 0L
    while (true) {
      val n = read(buf)
      if (n < 0) break
      out.write(buf, 0, n)
      total += n
    }
    return total
  }

  fun bufferedReader(charset: Charset? = null): BufferedReader = BufferedReader(readBytes().decodeToString())
  fun reader(charset: Charset? = null): BufferedReader = bufferedReader(charset)

  /** `buffered()` ِ JVM: جریانی که `mark`/`reset` دارد (همه‌اش در حافظه) */
  fun buffered(bufferSize: Int = 8192): InputStream = if (markSupported()) this else ByteArrayInputStream(readBytes())

  open fun markSupported(): Boolean = false
  open fun mark(readlimit: Int) {}
  open fun reset(): Unit = throw IOException("mark/reset not supported")
}

abstract class OutputStream : Closeable, Flushable {
  abstract fun write(b: Int)

  open fun write(b: ByteArray) = write(b, 0, b.size)

  open fun write(b: ByteArray, off: Int, len: Int) {
    for (i in off until off + len) write(b[i].toInt())
  }

  override fun flush() {}
  override fun close() {}

  fun bufferedWriter(charset: Charset? = null): BufferedWriter = BufferedWriter(OutputStreamWriter(this))
  fun buffered(bufferSize: Int = 8192): OutputStream = this
  fun writer(charset: Charset? = null): BufferedWriter = bufferedWriter(charset)
}

open class ByteArrayInputStream(private val buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset) : InputStream() {
  private var pos = offset
  private val end = minOf(buf.size, offset + length)

  override fun read(): Int = if (pos < end) buf[pos++].toInt() and 0xFF else -1

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (len == 0) return 0
    if (pos >= end) return -1
    val n = minOf(len, end - pos)
    buf.copyInto(b, off, pos, pos + n)
    pos += n
    return n
  }

  override fun available(): Int = end - pos

  private var marked = offset
  override fun markSupported(): Boolean = true
  override fun mark(readlimit: Int) { marked = pos }
  override fun reset() { pos = marked }
}

open class ByteArrayOutputStream(size: Int = 32) : OutputStream() {
  private var buf = ByteArray(maxOf(size, 16))
  private var count = 0

  private fun ensure(extra: Int) {
    if (count + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, count + extra))
  }

  override fun write(b: Int) {
    ensure(1)
    buf[count++] = b.toByte()
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    ensure(len)
    b.copyInto(buf, count, off, off + len)
    count += len
  }

  fun size(): Int = count
  fun reset() { count = 0 }
  fun toByteArray(): ByteArray = buf.copyOf(count)
  override fun toString(): String = buf.decodeToString(0, count)
  fun toString(charset: Charset): String = toString()
  fun toString(charsetName: String): String = toString()
  fun writeTo(out: OutputStream) = out.write(buf, 0, count)
}

/** فایل یک‌جا خوانده می‌شود (NSData)؛ همان `FileNotFoundException` ِ JDK اگر نیست */
open class FileInputStream(file: File) : ByteArrayInputStream(file.readBytes()) {
  constructor(path: String) : this(File(path))
}

/**
 *  در حافظه جمع می‌شود و در `close` یک‌جا با `write(2)` + `fsync(2)` روی دیسک می‌رود (`append` ⇐ ته‌نویسی).
 *  `fd.sync()` ِ پیش از بستن (الگوی «نوشتن، sync، rename» ِ دفتر) چیزی برای همگام کردن ندارد؛ خودِ `close` همگام است.
 */
open class FileOutputStream(private val file: File, private val append: Boolean = false) : ByteArrayOutputStream() {
  constructor(path: String, append: Boolean = false) : this(File(path), append)

  private var closed = false

  val fd: FileDescriptor = FileDescriptor()

  override fun close() {
    if (closed) return
    closed = true
    PosixFile.write(file.path, toByteArray(), append)
  }
}

class FileDescriptor internal constructor() {
  fun sync() {}
  fun valid(): Boolean = true
}

class BufferedReader internal constructor(private val text: String) : Closeable {
  private var pos = 0

  fun readText(): String = text.substring(pos).also { pos = text.length }

  fun readLine(): String? {
    if (pos >= text.length) return null
    val nl = text.indexOf('\n', pos)
    val end = if (nl < 0) text.length else nl
    val line = text.substring(pos, end).removeSuffix("\r")
    pos = if (nl < 0) text.length else nl + 1
    return line
  }

  fun readLines(): List<String> = generateSequence { readLine() }.toList()
  fun lineSequence(): Sequence<String> = generateSequence { readLine() }
  fun <T> useLines(block: (Sequence<String>) -> T): T = block(lineSequence())
  fun forEachLine(action: (String) -> Unit) = lineSequence().forEach(action)

  override fun close() {}
}

abstract class Writer : Closeable, Flushable, Appendable {
  abstract fun write(s: String)
  open fun write(c: Int) = write(c.toChar().toString())
  open fun write(cbuf: CharArray, off: Int, len: Int) = write(cbuf.concatToString(off, off + len))
  override fun append(value: Char): Writer { write(value.toString()); return this }
  override fun append(value: CharSequence?): Writer { write(value.toString()); return this }
  override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Writer { write(value.toString().substring(startIndex, endIndex)); return this }
  override fun flush() {}
  override fun close() {}
}

/** نوشته به UTF-8 روی جریان (iOS فقط UTF-8 دارد؛ ‎Charsets‎) */
open class OutputStreamWriter(private val out: OutputStream, charset: Charset? = null) : Writer() {
  override fun write(s: String) = out.write(s.encodeToByteArray())
  override fun flush() = out.flush()
  override fun close() = out.close()
}

class BufferedWriter(private val inner: Writer, private val size: Int = 8192) : Writer() {
  private val buf = StringBuilder()
  override fun write(s: String) { buf.append(s); if (buf.length >= size) flush() }
  fun newLine() = write("\n")
  override fun flush() { if (buf.isNotEmpty()) { inner.write(buf.toString()); buf.clear() }; inner.flush() }
  override fun close() { flush(); inner.close() }
}

/** همان JDK: همه‌چیز به جریانِ درونی، تا زیررده چیزی را عوض کند */
open class FilterOutputStream(protected val out: OutputStream) : OutputStream() {
  override fun write(b: Int) = out.write(b)
  override fun flush() = out.flush()
  override fun close() { flush(); out.close() }
}

open class InterruptedIOException(message: String? = null) : IOException(message)
