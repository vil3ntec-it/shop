package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppNav
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  PERF-DESK — نگهبانِ همیشگیِ CI برای پنجرهٔ کامپیوتر ([DeskShell]، همان درختِ `desktop/Main.kt`، لپ‌تاپِ ۱۳۶۶ نقطه):
 *
 *  - خانهٔ بی‌کار (پنجره فوکوس دارد، هیچ ورودی‌ای نیست): **صفر قاب و صفر ترکیب** — هیچ ساعت، شمارنده یا انیمیشنی در
 *    پوسته (سربرگ، منوی کناری، نوارِ وضعیت) و خانه نمی‌دود؛
 *  - صندوق با پنجرهٔ بی فوکوس (صاحب‌کار در برنامهٔ دیگری است): **صفر قاب** — تصویرِ متحرکِ صندوق (`V3Illustration`) هم
 *    مثلِ همهٔ چیزهای زنده با `V3Live` می‌ایستد و پردازنده را از برنامه‌های دیگر نمی‌گیرد.
 *
 *  دفترِ کوچک‌ترِ [ScreenSpeed] (همان شکل) تا آزمون چند ثانیه بماند؛ سنجهٔ عددی با دفترِ بزرگ: [DeskSpeedBenchTest].
 */
class DeskIdleGuardTest {

  @After fun release() { ShopStore.index(ShopData()); ShopStore.salesIndex(ShopData()) }

  private fun shell(block: (DeskShell) -> Unit) {
    val d = ScreenSpeed.shop(ScreenSpeed.Size(products = 1_500, sales = 2_000, suppliers = 20, purchases = 200, today = 40))
    ir.vil3ntec.tohid.desktop.TestBackend().use { backend ->
      backend.online = false
      DeskShell(DeskShell.loadedStore(d)).use { s ->
        s.open()
        s.run { settle(maxMs = 8_000) }
        block(s)
      }
    }
  }

  /**
   *  بی‌کاری در دو پنجرهٔ ۱٫۵ ثانیه‌ای: ساعتِ نوارِ وضعیت هر ۳۰ ثانیه یک تیک دارد (`rememberV3LiveClock`) و دست‌بالا در یکی
   *  از دو پنجرهٔ پشتِ هم می‌افتد؛ چیزی که پیوسته می‌دود (انیمیشن، تیکِ تند، حلقهٔ ترکیب) هر دو را پر می‌کند.
   */
  private fun quiet(s: DeskShell): DeskShell.Idle = s.run { idle(1_500).let { a -> if (a.frames == 0 && a.recompositions == 0) a else idle(1_500) } }

  @Test(timeout = 120_000) fun `خانهٔ بی‌کار — صفر قاب و صفر ترکیب؛ صندوقِ پنجرهٔ بی فوکوس — صفر قاب`() = shell { s ->
    s.run { AppNav.go("home"); settle(maxMs = 5_000) }
    val home = quiet(s)
    assertEquals("قاب‌های خانهٔ بی‌کار (${home.top})", 0, home.frames)
    assertEquals("ترکیب‌های خانهٔ بی‌کار (${home.top})", 0, home.recompositions)

    s.run { AppNav.go("sale"); settle(maxMs = 2_500) }
    s.run { focus(false); settle(maxMs = 2_000) }
    val away = quiet(s)
    s.run { focus(true) }
    assertEquals("قاب‌های صندوق با پنجرهٔ بی فوکوس (${away.top})", 0, away.frames)
  }
}
