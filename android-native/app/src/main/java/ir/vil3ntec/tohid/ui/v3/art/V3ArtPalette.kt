package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ir.vil3ntec.tohid.ui.theme.ArtInk
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors

/**
 *  نقش‌های رنگ در صحنه‌ها — نامِ کوچکِ هر کدام همان است که `tools/art` در دادهٔ صحنه می‌نویسد. محیط (زمین، سنگ، بوته،
 *  سایه، سیم) از تم؛ آدمک و چیزهای داستان از `ArtInk`.
 */
internal enum class ArtRole {
  GROUND, ROCK, ROCK2, LEAF, LEAF2, SHADOW, LINE,
  SKIN, SKIN2, BLUSH, NOSE, INK, BROW, HAIR, HAIRD, GREY, SHOE, WHITE,
  ORANGE, ORANGE2, TEAL, TEAL2, RED, RED2, NAVY, NAVY2, CREAM, CREAM2, MUSTARD, MUSTARD2, KRAFT, KRAFT2, PLUM, PLUM2, SKY, SKY2;

  companion object {
    private val byName = entries.associateBy { it.name.lowercase() }
    fun of(name: String): ArtRole = byName[name.lowercase()] ?: error("unknown art role: $name")
  }
}

/**
 *  رنگِ هر نقش برای تمِ جاری. محیط روی زمینهٔ صفحه (`bg`) ساخته می‌شود: روز سنگِ خاکستریِ روشن و خطِ زمینِ کمی
 *  تیره‌تر، شب سنگِ خاکستریِ خاموش و خطِ زمینِ روشن‌تر از زمینه؛ سیم در روز تقریباً سیاه و در شب خاکستریِ روشن.
 */
@Immutable
class V3ArtPalette internal constructor(private val colors: Array<Color>) {
  internal operator fun get(role: ArtRole): Color = colors[role.ordinal]

  companion object {
    fun from(c: PhoneV3Colors): V3ArtPalette {
      val dark = c.source.isDark
      val bg = c.bg
      val ink = c.ink
      val env = fun(day: Float, night: Float) = lerp(bg, ink, if (dark) night else day)
      val a = Array(ArtRole.entries.size) { Color.Unspecified }
      fun put(r: ArtRole, v: Color) { a[r.ordinal] = v }
      put(ArtRole.GROUND, env(0.13f, 0.26f))
      put(ArtRole.ROCK, env(0.075f, 0.13f))
      put(ArtRole.ROCK2, env(0.12f, 0.09f))
      put(ArtRole.SHADOW, env(0.05f, 0.06f))
      put(ArtRole.LINE, if (dark) lerp(ink, bg, 0.22f) else lerp(ink, bg, 0.04f))
      put(ArtRole.LEAF, if (dark) ArtInk.leafNight else ArtInk.leafDay)
      put(ArtRole.LEAF2, if (dark) ArtInk.leaf2Night else ArtInk.leaf2Day)
      put(ArtRole.SKIN, ArtInk.skin); put(ArtRole.SKIN2, ArtInk.skin2); put(ArtRole.BLUSH, ArtInk.blush); put(ArtRole.NOSE, ArtInk.nose)
      put(ArtRole.INK, ArtInk.ink); put(ArtRole.BROW, ArtInk.brow); put(ArtRole.HAIR, ArtInk.hair); put(ArtRole.HAIRD, ArtInk.hairD)
      put(ArtRole.GREY, ArtInk.grey); put(ArtRole.SHOE, ArtInk.shoe); put(ArtRole.WHITE, ArtInk.white)
      put(ArtRole.ORANGE, ArtInk.orange); put(ArtRole.ORANGE2, ArtInk.orange2); put(ArtRole.TEAL, ArtInk.teal); put(ArtRole.TEAL2, ArtInk.teal2)
      put(ArtRole.RED, ArtInk.red); put(ArtRole.RED2, ArtInk.red2); put(ArtRole.NAVY, ArtInk.navy); put(ArtRole.NAVY2, ArtInk.navy2)
      put(ArtRole.CREAM, ArtInk.cream); put(ArtRole.CREAM2, ArtInk.cream2); put(ArtRole.MUSTARD, ArtInk.mustard); put(ArtRole.MUSTARD2, ArtInk.mustard2)
      put(ArtRole.KRAFT, ArtInk.kraft); put(ArtRole.KRAFT2, ArtInk.kraft2); put(ArtRole.PLUM, ArtInk.plum); put(ArtRole.PLUM2, ArtInk.plum2)
      put(ArtRole.SKY, ArtInk.sky); put(ArtRole.SKY2, ArtInk.sky2)
      return V3ArtPalette(a)
    }
  }
}

/** پالتِ تمِ جاری؛ فقط با عوض شدنِ تم از نو ساخته می‌شود */
@Composable
fun rememberV3ArtPalette(): V3ArtPalette {
  val c = PhoneV3.colors
  return remember(c) { V3ArtPalette.from(c) }
}
