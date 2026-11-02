package ir.vil3ntec.tohid.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename
import platform.posix.strerror
import platform.posix.write
import kotlinx.cinterop.toKString

/*
 *  جایگزینِ iOS ِ `data/DiskWrite.kt` (همان API و همان تضمین): نوشتن در `‎.tmp‎`، `fsync`، جابه‌جاییِ
 *  اتمیِ `rename(2)` روی همان نام، و `fsync` ِ خودِ پوشه — پس از قطعِ برق یا بسته شدنِ ناگهانیِ برنامه یا
 *  فایلِ پیشین هست یا تازه، هرگز نیمه. (اندروید: FileChannel/`Files.move` ِ `java.nio`.)
 */
object DiskWrite {
  fun text(target: File, text: String) = bytes(target, text.encodeToByteArray())

  fun bytes(target: File, data: ByteArray) = stream(target) { it.write(data) }

  fun copy(source: File, target: File) = bytes(target, source.readBytes())

  fun stream(target: File, write: (OutputStream) -> Unit) {
    val dir = target.absoluteFile.parentFile ?: throw IOException("پوشهٔ «$target» معلوم نیست")
    if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) throw IOException("پوشهٔ «$dir» ساخته نشد")
    val tmp = File(dir, target.name + ".tmp")
    try {
      val buf = ByteArrayOutputStream()
      write(buf)
      writeDurably(tmp, buf.toByteArray())
      move(tmp, target)
    } catch (e: Throwable) {
      tmp.delete()
      throw e as? IOException ?: IOException(e.message ?: "نوشتن روی دیسک نشد", e)
    }
    syncDir(dir)
  }

  internal fun move(tmp: File, target: File) {
    if (rename(tmp.path, target.path) != 0) throw IOException("«${target.name}» روی دیسک جایگزین نشد: ${strerror(errno)?.toKString()}")
  }

  internal fun syncDir(dir: File) {
    val fd = open(dir.path, O_RDONLY)
    if (fd >= 0) { fsync(fd); close(fd) }
  }

  private fun writeDurably(file: File, data: ByteArray) {
    val fd = open(file.path, O_WRONLY or O_CREAT or O_TRUNC, 0x180)
    if (fd < 0) throw IOException("${file.path}: ${strerror(errno)?.toKString()}")
    try {
      var off = 0
      while (off < data.size) {
        val n = data.usePinned { write(fd, it.addressOf(off), (data.size - off).convert()) }
        if (n <= 0) throw IOException("${file.path}: ${strerror(errno)?.toKString()}")
        off += n.toInt()
      }
      if (fsync(fd) != 0) throw IOException("${file.path}: fsync ${strerror(errno)?.toKString()}")
    } finally {
      close(fd)
    }
  }
}
