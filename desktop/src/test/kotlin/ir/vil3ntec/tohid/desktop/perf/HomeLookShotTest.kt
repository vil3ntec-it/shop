package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 *  دستی: `TOHID_HOME_SHOT=<برچسب>` — عکسِ خانه (گوشیِ ۳۶۰dp، دفترِ قطعیِ `BigLedger.SMOKE` که «امروز» ندارد، پس با ساعتِ ماشین
 *  عوض نمی‌شود) در `desktop/build/home-look-<برچسب>.png`، پس از ۱۵۰ قابِ ۱۶ میلی‌ثانیه‌ای (پلکان و شمارنده‌ها نشسته). برای سنجیدنِ «ظاهر همان» پیش و پس
 *  از تغییرِ فقط-سرعت (`docs/PERFORMANCE-fa.md`).
 */
class HomeLookShotTest {
  @Test fun shot() {
    val tag = System.getenv("TOHID_HOME_SHOT").orEmpty()
    assumeTrue("فقط با TOHID_HOME_SHOT", tag.isNotBlank())
    val store = ScreenFrame.storeOf(BigLedger.make(BigLedger.SMOKE))
    val out = File(System.getProperty("user.dir"), "build/home-look-$tag.png")
    ScreenFrame.signedIn { ScreenFrame.measure(360, 760, 150, png = out) { V3RoutePreview("home", store, dark = false) } }
    println("[home-shot] $out ${out.length()} bytes")
  }
}
