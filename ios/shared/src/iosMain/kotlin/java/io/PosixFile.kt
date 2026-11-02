package java.io

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.O_APPEND
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fsync
import platform.posix.open
import platform.posix.strerror

/** نوشتنِ کامل و همگام (`fsync`) — پایهٔ `FileOutputStream` ِ shim */
internal object PosixFile {
  fun write(path: String, data: ByteArray, append: Boolean) {
    val flags = O_WRONLY or O_CREAT or (if (append) O_APPEND else O_TRUNC)
    val fd = open(path, flags, 0x180)
    if (fd < 0) throw FileNotFoundException("$path (${strerror(errno)?.toKString()})")
    try {
      var off = 0
      while (off < data.size) {
        val n = data.usePinned { platform.posix.write(fd, it.addressOf(off), (data.size - off).convert()) }
        if (n <= 0) throw IOException("$path: ${strerror(errno)?.toKString()}")
        off += n.toInt()
      }
      if (fsync(fd) != 0) throw IOException("$path: fsync ${strerror(errno)?.toKString()}")
    } finally {
      close(fd)
    }
  }
}
