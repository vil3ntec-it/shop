package ir.vil3ntec.tohid.desktop

import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.io.File
import javax.imageio.ImageIO

/**
 *  ابزارِ سنجشِ چشمی — فقط با `-Dtohid.shot=<پوشه>` روشن می‌شود.
 *
 *  هر چند ثانیه از پنجره عکس می‌گیرد (`shot-1.png`، …) تا برنامهٔ
 *  واقعی روی یک نمایشگرِ مجازی (Xvfb در CI) دیده و سنجیده شود. در
 *  نسخهٔ دستِ کاربر این ویژگی تنظیم نمی‌شود و هیچ کاری نمی‌کند.
 */
object DevShot {
  fun start() {
    val dir = System.getProperty("tohid.shot")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
    val every = System.getProperty("tohid.shot.every")?.toLongOrNull() ?: 4000
    val count = System.getProperty("tohid.shot.count")?.toIntOrNull() ?: 3
    dir.mkdirs()
    Thread({
      runCatching {
        val robot = Robot()
        for (i in 1..count) {
          Thread.sleep(every)
          val w = Window.getWindows().firstOrNull { it.isShowing } ?: continue
          val b = w.bounds
          ImageIO.write(robot.createScreenCapture(Rectangle(b.x, b.y, b.width, b.height)), "png", File(dir, "shot-$i.png"))
        }
      }.onFailure { it.printStackTrace() }
      if (System.getProperty("tohid.shot.exit") == "true") Runtime.getRuntime().halt(0)
    }, "dev-shot").apply { isDaemon = true }.start()
  }
}
