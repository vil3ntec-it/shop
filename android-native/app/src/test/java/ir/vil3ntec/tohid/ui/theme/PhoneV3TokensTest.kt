package ir.vil3ntec.tohid.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

/**
 *  یک منبعِ رنگ (design-v2): `PhoneV3Colors`، `PhoneV3Ui`، `PhoneV3Roles`، `PhoneV3Palette` و رنگِ
 *  «در صف»ِ پیک همه از `ShopColors` ساخته می‌شوند و در فایل‌هایشان هیچ HEX ِ خودشان نیست.
 */
class PhoneV3TokensTest {
  private val pairs = listOf(LightColors to PhoneV3Light, DarkColors to PhoneV3Dark)

  @Test
  fun `رنگ‌های گوشی همان توکن‌های ShopColors`() {
    pairs.forEach { (s, p) ->
      assertEquals(s.background, p.bg)
      assertEquals(s.surface, p.card)
      assertEquals(s.textPrimary, p.ink)
      assertEquals(s.textSecondary, p.inkMuted)
      assertEquals(s.borderCard, p.hairline)
      assertEquals(s.icon, p.icon)
      assertEquals(s.textMuted, p.chevron)
      assertEquals(s.inverse, p.black)
      assertEquals(s.onInverse, p.onBlack)
      assertEquals(s.brandDeep, p.accentDeep)
      assertEquals(s.brandPrimary, p.toggleOn)
      assertEquals(s.surfaceElevated, p.tile)
      assertEquals(s.status.delivered.fg, p.positive)
      assertEquals(s.status.cancelled.fg, p.negative)
      assertEquals("رنگِ «در صف» ِ پیک (هشدار، کهربایی)", s.status.warning.fg, p.waiting)
    }
    assertSame(PhoneV3Dark, PhoneV3.of(DarkColors.background))
    assertSame(PhoneV3Light, PhoneV3.of(LightColors.background))
  }

  @Test
  fun `دکمه‌ها، وضعیت‌ها و پیام‌ها همان ShopColors — برای پیک و کیت`() {
    listOf(LightColors to PhoneV3UiLight, DarkColors to PhoneV3UiDark).forEach { (s, ui) ->
      assertEquals(s.button.primary.bg, ui.primary)
      assertEquals(s.button.secondary.text, ui.secondaryFg)
      assertEquals(s.button.disabled.bg, ui.disabledBg)
      assertEquals(s.iconActive, ui.iconActive)
      assertEquals(s.status.inDelivery.fg, ui.delivery.fg)
      assertEquals(s.status.needsReview.bg, ui.review.bg)
    }
    pairs.forEach { (s, p) ->
      assertEquals(s.button.primary.bg, PhoneV3Roles.button(p, V3ButtonKind.PRIMARY).bg)
      assertEquals(s.button.disabled.text, PhoneV3Roles.disabled(p).fg)
      assertEquals(s.status.needsReview.fg, PhoneV3Roles.status(p, V3Status.REVIEW).fg)
      assertEquals(s.message.warning.icon, PhoneV3Roles.toneIcon(p, V3Tone.WARNING))
      assertEquals(s.borderInput, PhoneV3Roles.inputBorder(p))
      assertEquals(s.chart.series, PhoneV3Palette.chartSeries(p))
      assertEquals(s.chart.grid, PhoneV3Palette.chartGrid(p))
      assertEquals(s.surfaceSelected, PhoneV3Palette.selected(p))
    }
  }

  @Test
  fun `فایل‌های رنگِ گوشی هیچ HEX ِ خودشان ندارند`() {
    val theme = listOf(
      "src/main/java/ir/vil3ntec/tohid/ui/theme",
      "../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/theme",
      "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/theme",
    ).map { File(it) }.first { it.isDirectory }
    listOf("PhoneV3.kt", "PhoneV3Roles.kt").forEach { name ->
      val hex = Regex("""Color\(0x""").findAll(File(theme, name).readText()).count()
      assertEquals("$name HEX دارد", 0, hex)
    }
  }
}
