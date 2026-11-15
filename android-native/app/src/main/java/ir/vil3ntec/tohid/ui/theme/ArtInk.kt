package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color

/**
 *  رنگ‌های ثابتِ آدمک‌های صحنه‌های خالی و خطا (دستهٔ ۶۵، e-rows-65a) — پوست، مو، لباس و چیزهای داستان (کارتن، ابر،
 *  موتور). این‌ها در روز و شب یکی‌اند (پوست و پیراهن در شب عوض نمی‌شود)؛ رنگِ محیط (سنگ، زمین، خطِ زمین، سیم)
 *  از توکن‌های تم ساخته می‌شود (`V3ArtPalette`) تا شب درست بنشیند. سبزِ بوته دو جفتِ روز/شب دارد.
 *  همتای پیش‌نمایشِ طراحی: `tools/art/palette.mjs` — هر دو با هم عوض شوند.
 */
object ArtInk {
  val skin = Color(0xFFF6C699)
  val skin2 = Color(0xFFEBB384)
  val blush = Color(0xFFF3A884)
  val nose = Color(0xFFED9366)
  val ink = Color(0xFF2A1E17)
  val brow = Color(0xFF3B2416)
  val hair = Color(0xFF523011)
  val hairD = Color(0xFF2E221A)
  val grey = Color(0xFFC9C2BA)
  val shoe = Color(0xFF3B2A20)
  val white = Color(0xFFFFFFFF)
  val orange = Color(0xFFEF9532)
  val orange2 = Color(0xFFE07B2F)
  val teal = Color(0xFF3E9EA3)
  val teal2 = Color(0xFF2F8288)
  val red = Color(0xFFE0574A)
  val red2 = Color(0xFFC64538)
  val navy = Color(0xFF3A4A66)
  val navy2 = Color(0xFF2E3B52)
  val cream = Color(0xFFF7EFE1)
  val cream2 = Color(0xFFE6D7BD)
  val mustard = Color(0xFFF2BE45)
  val mustard2 = Color(0xFFDDA52E)
  val kraft = Color(0xFFDDA66B)
  val kraft2 = Color(0xFFC88C52)
  val plum = Color(0xFF8E5A9B)
  val plum2 = Color(0xFF774985)
  val sky = Color(0xFF7DB9E8)
  val sky2 = Color(0xFF5E9ED3)

  /** سبزِ بوته و برگ: روز / شب (شب کمی خاموش‌تر تا روی زمینهٔ تیره نسوزد) */
  val leafDay = Color(0xFF569230)
  val leaf2Day = Color(0xFF67A23A)
  val leafNight = Color(0xFF3F7428)
  val leaf2Night = Color(0xFF4C8A31)
}
