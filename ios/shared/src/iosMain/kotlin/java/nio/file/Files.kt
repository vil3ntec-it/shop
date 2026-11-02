package java.nio.file

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/** shimِ iOS ِ کمترینِ `java.nio.file` (خواندنِ دفتر در `LocalSnapshots`): مسیر و جریانِ خواندن */
class Path internal constructor(internal val file: File) {
  fun toFile(): File = file
  override fun toString(): String = file.path
}



object Files {
  fun newInputStream(path: Path, vararg options: Any): InputStream = FileInputStream(path.file)
  fun exists(path: Path, vararg options: Any): Boolean = path.file.exists()
  fun size(path: Path): Long = path.file.length()
  fun readAllBytes(path: Path): ByteArray = path.file.readBytes()

  /** `rename(2)` — جایگزینِ اتمی (همان ATOMIC_MOVE/REPLACE_EXISTING روی یک دیسک) */
  fun move(source: Path, target: Path, vararg options: StandardCopyOption): Path {
    if (platform.posix.rename(source.file.path, target.file.path) != 0) throw java.io.IOException("move ${source.file.name}")
    return target
  }
}

enum class StandardCopyOption { REPLACE_EXISTING, ATOMIC_MOVE, COPY_ATTRIBUTES }
