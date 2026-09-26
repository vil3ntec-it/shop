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
        fun win() = Window.getWindows().firstOrNull { it.isShowing }
        fun shot(name: String) {
          val b = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
          ImageIO.write(robot.createScreenCapture(Rectangle(b.x, b.y, b.width, b.height)), "png", File(dir, "$name.png"))
        }
        val script = System.getProperty("tohid.shot.script")
        if (script.isNullOrBlank()) {
          for (i in 1..count) { Thread.sleep(every); shot("shot-$i") }
        } else {
          //  فرمان‌ها: wait:ms · click:x,y (روی صفحه) · type:متن · key:ENTER|ESCAPE|TAB · shot:نام
          Thread.sleep(every)
          java.awt.EventQueue.invokeAndWait { win()?.setLocation(0, 0) }
          Thread.sleep(500)
          for (cmd in script.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val (op, arg) = cmd.substringBefore(':') to cmd.substringAfter(':', "")
            when (op) {
              "wait" -> Thread.sleep(arg.toLong())
              "shot" -> shot(arg)
              "click" -> {
                val (x, y) = arg.split(',').map { it.trim().toInt() }
                robot.mouseMove(x, y); Thread.sleep(80)
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                Thread.sleep(400)
              }
              "type" -> {
                val sel = java.awt.datatransfer.StringSelection(arg)
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
                Thread.sleep(300)
                robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL); robot.keyPress(java.awt.event.KeyEvent.VK_V)
                robot.keyRelease(java.awt.event.KeyEvent.VK_V); robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL)
                Thread.sleep(200)
              }
              "key" -> {
                val code = java.awt.event.KeyEvent::class.java.getField("VK_$arg").getInt(null)
                robot.keyPress(code); robot.keyRelease(code); Thread.sleep(250)
              }
            }
          }
        }
      }.onFailure { it.printStackTrace() }
      if (System.getProperty("tohid.shot.exit") == "true") Runtime.getRuntime().halt(0)
    }, "dev-shot").apply { isDaemon = true }.start()
  }
}
