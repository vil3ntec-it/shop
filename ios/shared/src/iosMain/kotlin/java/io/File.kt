package java.io

import ir.vil3ntec.tohid.ios.jvm.Charset
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy

/*
 *  shimِ iOS ِ `java.io.File` — فقط مسیر و کارهای سادهٔ دیسک (NSFileManager)، با همان نام‌ها و
 *  همان قراردادِ JDK (`exists`، `mkdirs`، `delete` ⇐ Boolean؛ خواندنِ فایلِ نبوده ⇐ IOException).
 *  جریان‌ها ‎Streams.kt‎ (یک‌جا در حافظه، نوشتنِ اتمی در `close`)؛ قفلِ فایل (`FileChannel`) نیست.
 *  ⛔ نوشتن اتمی است (`writeToFile:atomically:YES`)، مثلِ الگوی «.tmp سپس rename» ِ دفترِ فروشگاه.
 */
class File(path: String) : Comparable<File> {
  val path: String = normalize(path)

  constructor(parent: String?, child: String) : this(if (parent.isNullOrEmpty()) child else "$parent/$child")
  constructor(parent: File?, child: String) : this(parent?.path, child)

  val name: String get() = path.substringAfterLast('/')
  val parent: String? get() = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
  val parentFile: File? get() = parent?.let { File(it) }
  val absolutePath: String get() = path
  val absoluteFile: File get() = this
  val canonicalPath: String get() = path
  val isFile: Boolean get() = exists() && !isDirectory
  val isDirectory: Boolean
    get() = memScoped {
      val dir = alloc<BooleanVar>()
      fm.fileExistsAtPath(path, isDirectory = dir.ptr) && dir.value
    }

  fun exists(): Boolean = fm.fileExistsAtPath(path)
  fun mkdirs(): Boolean = !exists() && fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
  fun mkdir(): Boolean = !exists() && fm.createDirectoryAtPath(path, withIntermediateDirectories = false, attributes = null, error = null)
  fun delete(): Boolean = exists() && (!isDirectory || list().isNullOrEmpty()) && fm.removeItemAtPath(path, error = null)
  fun deleteRecursively(): Boolean = !exists() || fm.removeItemAtPath(path, error = null)
  fun renameTo(dest: File): Boolean {
    if (dest.exists()) fm.removeItemAtPath(dest.path, error = null)
    return fm.moveItemAtPath(path, toPath = dest.path, error = null)
  }

  fun length(): Long = (fm.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
  fun lastModified(): Long =
    ((fm.attributesOfItemAtPath(path, error = null)?.get(NSFileModificationDate) as? NSDate)?.timeIntervalSince1970?.times(1000))?.toLong() ?: 0L

  fun setLastModified(time: Long): Boolean = exists() && fm.setAttributes(
    mapOf<Any?, Any?>(platform.Foundation.NSFileModificationDate to platform.Foundation.NSDate.dateWithTimeIntervalSince1970(time / 1000.0)),
    ofItemAtPath = path, error = null,
  )

  fun list(): Array<String>? = if (!isDirectory) null else fm.contentsOfDirectoryAtPath(path, error = null)?.map { it.toString() }?.toTypedArray()
  fun listFiles(): Array<File>? = list()?.map { File(this, it) }?.toTypedArray()


  fun readBytes(): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: throw FileNotFoundException("$path (No such file or directory)")
    val out = ByteArray(data.length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return out
  }

  fun readText(): String = readBytes().decodeToString()
  fun readText(charset: Charset): String = readText()

  fun writeBytes(bytes: ByteArray) {
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    if (!data.writeToFile(path, atomically = true)) throw IOException("$path: نوشتن نشد")
  }

  fun writeText(text: String) = writeBytes(text.encodeToByteArray())
  fun writeText(text: String, charset: Charset) = writeText(text)
  fun appendText(text: String) = writeBytes((if (exists()) readBytes() else ByteArray(0)) + text.encodeToByteArray())
  fun appendText(text: String, charset: Charset) = appendText(text)

  fun canWrite(): Boolean = fm.isWritableFileAtPath(path)
  fun isHidden(): Boolean = name.startsWith(".")
  val usableSpace: Long
    get() = (fm.attributesOfFileSystemForPath(path, error = null)?.get(platform.Foundation.NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
  val freeSpace: Long get() = usableSpace
  fun setReadable(readable: Boolean): Boolean = true
  fun setWritable(writable: Boolean): Boolean = true
  fun deleteOnExit() {}
  val isAbsolute: Boolean get() = path.startsWith("/")
  val extension: String get() = name.substringAfterLast('.', "")
  val nameWithoutExtension: String get() = name.substringBeforeLast(".")
  val invariantSeparatorsPath: String get() = path
  fun resolve(relative: String): File = if (relative.startsWith("/")) File(relative) else File(this, relative)
  fun resolve(relative: File): File = resolve(relative.path)
  fun relativeTo(base: File): File {
    val b = base.path.trimEnd('/') + "/"
    if (!path.startsWith(b) && path != base.path) throw IllegalArgumentException("this and base files have different roots: $this and $base.")
    return File(path.removePrefix(b).let { if (path == base.path) "" else it })
  }
  fun startsWith(other: File): Boolean = path == other.path || path.startsWith(other.path.trimEnd('/') + "/")
  fun appendBytes(array: ByteArray) = writeBytes((if (exists()) readBytes() else ByteArray(0)) + array)
  fun reader(charset: Charset? = null): BufferedReader = bufferedReader()
  fun listFiles(filter: FileFilter): Array<File>? = listFiles()?.filter { filter.accept(it) }?.toTypedArray()
  fun listFiles(filter: FilenameFilter): Array<File>? = listFiles()?.filter { filter.accept(this, it.name) }?.toTypedArray()

  fun toPath(): java.nio.file.Path = java.nio.file.Path(this)
  fun inputStream(): InputStream = FileInputStream(this)
  fun outputStream(): OutputStream = FileOutputStream(this)
  fun bufferedReader(charset: Charset? = null): BufferedReader = inputStream().bufferedReader()
  fun readLines(charset: Charset? = null): List<String> = bufferedReader().readLines()
  fun forEachLine(charset: Charset? = null, action: (String) -> Unit) = bufferedReader().forEachLine(action)
  fun createNewFile(): Boolean = if (exists()) false else { writeBytes(ByteArray(0)); true }
  fun canRead(): Boolean = exists()
  fun walk(): Sequence<File> = sequence {
    yield(this@File)
    if (isDirectory) listFiles()?.forEach { yieldAll(it.walk()) }
  }
  fun walkTopDown(): Sequence<File> = walk()
  fun copyTo(target: File, overwrite: Boolean = false): File {
    if (target.exists() && !overwrite) throw IOException("${target.path}: هست")
    target.writeBytes(readBytes())
    return target
  }

  override fun compareTo(other: File): Int = path.compareTo(other.path)
  override fun equals(other: Any?): Boolean = other is File && other.path == path
  override fun hashCode(): Int = path.hashCode() xor 1234321
  override fun toString(): String = path

  companion object {
    const val separator: String = "/"
    const val separatorChar: Char = '/'
    const val pathSeparator: String = ":"
    const val pathSeparatorChar: Char = ':'
    private val fm: NSFileManager get() = NSFileManager.defaultManager
    private fun normalize(p: String): String = if (p.length > 1) p.trimEnd('/') else p
  }
}

fun interface FileFilter {
  fun accept(pathname: File): Boolean
}

fun interface FilenameFilter {
  fun accept(dir: File, name: String): Boolean
}

open class FileNotFoundException(message: String? = null) : IOException(message)
