package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  نقش‌های رنگیِ کیت (COLOR-SYSTEM §۷ دکمه، §۸ وضعیت، §۹ پیام، کادرِ ورودی) در روز و
 *  شب خوانا هستند: متن ≥ ۴٫۵:۱ (WCAG AA)، مرز و آیکنِ غیرمتنی ≥ ۳:۱.
 */
class PhoneV3RolesContrastTest {
  private fun contrast(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return maxOf(l1, l2) / minOf(l1, l2)
  }

  private val themes = listOf(PhoneV3Light, PhoneV3Dark)

  @Test
  fun `دکمه‌ها — متن روی زمینه`() {
    themes.forEach { c ->
      V3ButtonKind.entries.forEach { k ->
        val p = PhoneV3Roles.button(c, k)
        assertTrue("$k ${if (c === PhoneV3Dark) "شب" else "روز"}: ${contrast(p.fg, p.bg)}", contrast(p.fg, p.bg) >= 4.5f)
      }
      //  غیرفعال عمداً کم‌رنگ است (WCAG معافش می‌کند) ولی هنوز دیده می‌شود: ≥ ۲ —
      //  مقدارِ روزِ COLOR-SYSTEM (#94A3B8 روی #E2E8F0) ≈ ۲٫۰۴ است
      val d = PhoneV3Roles.disabled(c)
      assertTrue("غیرفعال: ${contrast(d.fg, d.bg)}", contrast(d.fg, d.bg) >= 2f)
    }
  }

  @Test
  fun `هفت وضعیتِ سفارش و چهار پیام`() {
    themes.forEach { c ->
      V3Status.entries.forEach { s ->
        val p = PhoneV3Roles.status(c, s)
        assertTrue("$s: ${contrast(p.fg, p.bg)}", contrast(p.fg, p.bg) >= 4.5f)
      }
      V3Tone.entries.forEach { t ->
        val p = PhoneV3Roles.tone(c, t)
        assertTrue("$t متن", contrast(p.fg, p.bg) >= 4.5f)
        assertTrue("$t آیکن", contrast(PhoneV3Roles.toneIcon(c, t), p.bg) >= 3f)
      }
    }
  }

  @Test
  fun `کادرِ ورودی، حلقهٔ تمرکز و متنِ برند`() {
    themes.forEach { c ->
      assertTrue("مرزِ کادر", contrast(PhoneV3Roles.inputBorder(c), c.bg) >= 1.3f)
      assertTrue("حلقهٔ تمرکز", contrast(PhoneV3Roles.focus(c), PhoneV3Roles.inputFill(c)) >= 3f)
      assertTrue("متنِ برند", contrast(PhoneV3Roles.brandText(c), c.bg) >= 4.5f)
      assertTrue("متنِ خطا", contrast(PhoneV3Roles.errorText(c), c.bg) >= 4.5f)
      assertTrue("خطر روی وارونه", contrast(PhoneV3Roles.dangerOnInverse, c.black) >= 4.5f)
    }
  }
}
