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
        //  فقط خودِ پنجره، نه کلِ نمایشگر — عکسِ ۱۲۸۰×۸۰۰ همان ۱۲۸۰×۸۰۰ است، بی حاشیهٔ سیاه
        fun shot(name: String) {
          val screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
          val b = win()?.bounds?.intersection(screen)?.takeIf { it.width > 0 && it.height > 0 } ?: screen
          ImageIO.write(robot.createScreenCapture(Rectangle(b.x, b.y, b.width, b.height)), "png", File(dir, "$name.png"))
        }
        val script = System.getProperty("tohid.shot.script")
        if (script.isNullOrBlank()) {
          for (i in 1..count) { Thread.sleep(every); shot("shot-$i") }
        } else {
          //  فرمان‌ها: wait:ms · size:w,h (اندازهٔ پنجره) · click:x,y (روی صفحه) · type:متن · typecmd:فرمانِ پوسته (خروجی‌اش تایپ می‌شود) · key:ENTER|ESCAPE|TAB · shot:نام
          Thread.sleep(every)
          repeat(4) { java.awt.EventQueue.invokeAndWait { win()?.setLocation(0, 0) }; Thread.sleep(250) }
          for (cmd in script.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val (op, arg) = cmd.substringBefore(':') to cmd.substringAfter(':', "")
            when (op) {
              "wait" -> Thread.sleep(arg.toLong())
              //  اندازهٔ پنجره، مثلِ size:1280,800 — برای عکس در چند اندازهٔ واقعیِ مانیتور
              "size" -> {
                val (w, h) = arg.split(',').map { it.trim().toInt() }
                java.awt.EventQueue.invokeAndWait { win()?.setBounds(0, 0, w, h) }
                Thread.sleep(1200)
              }
              //  رفتن به یک صفحه با همان مسیرِ میان‌برها (`AppNav`) — بی وابستگی به فوکوسِ صفحه‌کلید
              "go" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.AppNav.go(arg) }; Thread.sleep(1500) }
              //  Ctrl+کلید، مثلِ ctrl:1
              "ctrl" -> {
                val code = java.awt.event.KeyEvent::class.java.getField("VK_$arg").getInt(null)
                robot.keyPress(java.awt.event.KeyEvent.VK_CONTROL); robot.keyPress(code)
                robot.keyRelease(code); robot.keyRelease(java.awt.event.KeyEvent.VK_CONTROL); Thread.sleep(400)
              }
              //  پروندهٔ مشتری: cust:first · زبانه‌اش: custtab:0|1|2
              "cust" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.screens.DeskCustomersShot.open.value = arg }; Thread.sleep(1200) }
              "custtab" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.screens.DeskCustomersShot.tab.value = arg.toInt() }; Thread.sleep(1000) }
              //  برگشت و تعویض (RET): ret:first (فرمِ تازه‌ترین فاکتور) · ret:none (بی رسید) · ret:close
              "ret" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.screens.ReturnsShot.open.value = arg }; Thread.sleep(1200) }
              //  نمای کالای صندوق: posview:card|table — همان کلیدِ «کارتی/جدولی» (`PosShot`)
              "posview" -> {
                java.awt.EventQueue.invokeAndWait {
                  ir.vil3ntec.tohid.ui.components.pos.PosShot.view.value = ir.vil3ntec.tohid.ui.components.common.ViewMode.parse(arg)
                }
                Thread.sleep(1000)
              }
              //  «موردِ تازه» در همین صفحه — همان Ctrl+N
              "new" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.AppNav.addAction.value?.invoke() }; Thread.sleep(1200) }
              //  تخفیف‌ها: promo:demo — پنج قاعدهٔ نمونه روی دفترِ نمایشی، کلید روشن (`PromoLive.demo`)
              "promo" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.PromoLive.demo() }; Thread.sleep(800) }
              "help" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.AppNav.help.value = arg != "off" }; Thread.sleep(1500) }
              //  محصولات: نما (prodview:card|table) و انتخابِ n ردیفِ اولِ فهرست (prodsel:3 — نوارِ کارِ گروهی)
              "prodview" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.screens.DeskProductsShot.view.value = arg }; Thread.sleep(1200) }
              //  دستگاه‌ها: پیش‌نمایشِ بی چاپگر — hwshot:receipt|label|a4|close (HW-11)
              //  و نمایشگرِ مشتری — hwshot:customer|nocustomer (HW-10)
              "hwshot" -> {
                java.awt.EventQueue.invokeAndWait {
                  when (arg) {
                    "customer" -> ir.vil3ntec.tohid.hw.CustomerFacing.open.value = true
                    "nocustomer" -> ir.vil3ntec.tohid.hw.CustomerFacing.open.value = false
                    else -> ir.vil3ntec.tohid.ui.screens.HwShot.preview.value = arg.takeIf { it != "close" }
                  }
                }
                Thread.sleep(1500)
              }
              "prodsel" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.screens.DeskProductsShot.select.value = arg.toInt() }; Thread.sleep(1000) }
              //  تم: theme:DARK|LIGHT|SYSTEM — همان درخواستِ نوارِ منو (`AppNav.theme`)
              "theme" -> { java.awt.EventQueue.invokeAndWait { ir.vil3ntec.tohid.ui.AppNav.theme.value = arg }; Thread.sleep(1500) }
              "scroll" -> { robot.mouseWheel(arg.toInt()); Thread.sleep(600) }
              "move" -> { val (x, y) = arg.split(',').map { it.trim().toInt() }; robot.mouseMove(x, y); Thread.sleep(150) }
              "rclick" -> {
                val (x, y) = arg.split(',').map { it.trim().toInt() }
                robot.mouseMove(x, y); Thread.sleep(80)
                robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK)
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK)
                Thread.sleep(500)
              }
              "shot" -> shot(arg)
              "click" -> {
                val (x, y) = arg.split(',').map { it.trim().toInt() }
                robot.mouseMove(x, y); Thread.sleep(80)
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                Thread.sleep(400)
              }
              "type", "typecmd" -> {
                val text = if (op == "type") arg
                  else ProcessBuilder("sh", "-c", arg).start().inputStream.bufferedReader().readText().trim()
                val sel = java.awt.datatransfer.StringSelection(text)
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
