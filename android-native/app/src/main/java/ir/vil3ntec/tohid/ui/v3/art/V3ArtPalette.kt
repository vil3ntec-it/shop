package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors

/**
 *  رنگ‌های تصویرها — همه از توکن‌های تمِ جاری (`PhoneV3Colors` ⇐ `ShopColors`)، هیچ HEX ِ تازه‌ای نیست؛ آمیختن و
 *  شفافیت فقط. روز و شب هر دو از همین ساخته می‌شوند: سطحِ کاغذ و جعبه در شب کمی روشن‌تر از کارت است تا در زمینهٔ تیره
 *  گم نشود، و سایه در شب سیاه‌تر و پررنگ‌تر.
 */
@Immutable
class V3ArtPalette(
  /** دایرهٔ نرمِ پشتِ تصویر */
  val halo: Color,
  val haloRim: Color,
  /** سایهٔ زمین و لایه‌های سایهٔ نرم */
  val shadow: Color,
  /** خطِ دورِ چیزها */
  val line: Color,
  /** کاغذ، رسید و سطحِ روشن */
  val paper: Color,
  /** رو و پهلوی چیزِ اصلی (قفسه، سبد) */
  val face: Color,
  val side: Color,
  /** کارتنِ جعبه */
  val kraft: Color,
  val kraftSide: Color,
  val brand: Color,
  val brandSoft: Color,
  val leaf: Color,
  val leafDeep: Color,
  val leafSoft: Color,
  val trunk: Color,
  /** کهربایی: سکه، آذرخش، جرقه */
  val warm: Color,
  val coral: Color,
  val sky: Color,
  val violet: Color,
  val danger: Color,
  val ink: Color,
  val muted: Color,
) {
  companion object {
    fun from(c: PhoneV3Colors): V3ArtPalette {
      val s = c.source
      val dark = s.isDark
      val amber = s.accent(3)
      val lime = s.accent(6)
      val leaf = lerp(c.positive, lime, 0.35f)
      val paper = if (dark) lerp(c.card, c.ink, 0.14f) else c.card
      return V3ArtPalette(
        halo = s.brandPrimary.copy(alpha = if (dark) 0.16f else 0.09f),
        haloRim = s.brandPrimary.copy(alpha = if (dark) 0.26f else 0.16f),
        shadow = if (dark) Color.Black.copy(alpha = 0.42f) else s.shadow.copy(alpha = 0.16f),
        line = lerp(c.ink, s.brandDeep, if (dark) 0.15f else 0.35f).copy(alpha = if (dark) 0.78f else 0.86f),
        paper = paper,
        face = if (dark) lerp(c.tile, c.ink, 0.10f) else c.tile,
        side = lerp(c.tile, c.ink, if (dark) 0.02f else 0.12f),
        kraft = lerp(amber, paper, if (dark) 0.45f else 0.58f),
        kraftSide = lerp(amber, paper, if (dark) 0.25f else 0.38f),
        brand = s.brandPrimary,
        brandSoft = lerp(s.brandPrimary, paper, 0.55f),
        leaf = leaf,
        leafDeep = lerp(leaf, if (dark) c.bg else c.ink, 0.28f),
        leafSoft = lerp(leaf, paper, 0.38f),
        trunk = if (dark) lerp(amber, c.bg, 0.38f) else lerp(amber, c.ink, 0.48f),
        warm = amber,
        coral = s.accent(2),
        sky = s.accent(0),
        violet = s.accent(1),
        danger = c.negative,
        ink = c.ink,
        muted = c.inkMuted,
      )
    }
  }
}

/** پالتِ تمِ جاری؛ فقط با عوض شدنِ تم از نو ساخته می‌شود */
@Composable
fun rememberV3ArtPalette(): V3ArtPalette {
  val c = PhoneV3.colors
  return remember(c) { V3ArtPalette.from(c) }
}
