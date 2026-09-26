package ir.vil3ntec.tohid.desktop

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock

/**
 *  فقط یک نمونه از برنامه روی هر پوشهٔ داده.
 *
 *  دو پنجرهٔ هم‌زمان یعنی دو `ShopStore` که هر کدام دفتر را جدا می‌نویسند
 *  و آخری برنده است — فروشِ پنجرهٔ اول بی‌صدا گم می‌شد. پس قفلِ فایل؛ و
 *  اجرای دوم (مثلاً دوباره زدنِ میان‌بُر وقتی برنامه در سینی است) از
 *  راهِ یک درگاهِ محلی (فقط 127.0.0.1) به اولی می‌گوید «جلو بیا» و بسته
 *  می‌شود.
 */
object SingleInstance {

  @Volatile var onActivate: (() -> Unit)? = null
  private var lock: FileLock? = null

  fun acquire(): Boolean {
    val root = DesktopContext.root
    val portFile = File(root, "instance.port")
    val channel = RandomAccessFile(File(root, "instance.lock"), "rw").channel
    val got = runCatching { channel.tryLock() }.getOrNull()
    if (got == null) {
      //  یکی دیگر باز است — صدایش بزن و برو
      runCatching {
        val port = portFile.readText().trim().toInt()
        Socket(InetAddress.getLoopbackAddress(), port).use { it.getOutputStream().write("show\n".toByteArray()) }
      }
      return false
    }
    lock = got
    runCatching {
      val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
      portFile.writeText(server.localPort.toString())
      Thread({
        while (true) {
          runCatching {
            server.accept().use { s ->
              if (s.getInputStream().bufferedReader().readLine() == "show") onActivate?.invoke()
            }
          }
        }
      }, "single-instance").apply { isDaemon = true }.start()
    }
    return true
  }

  fun front() { onActivate?.invoke() }
}
