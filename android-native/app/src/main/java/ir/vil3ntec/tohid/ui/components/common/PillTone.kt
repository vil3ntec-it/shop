package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.ShopColors

/** معنای رنگِ نشان — رنگ هیچ‌جا تزیین نیست */
enum class PillTone { SUCCESS, WARNING, DANGER, NEUTRAL }

/**
 *  رنگِ متن/نقطه و زمینهٔ هر نشان. متن روی زمینهٔ کم‌رنگِ همان رنگ می‌نشیند؛
 *  همهٔ جفت‌ها در `BackOfficeContrastTest` ≥ ۴٫۵:۱ سنجیده می‌شوند.
 */
fun pillColors(c: ShopColors, tone: PillTone): Pair<Color, Color> = when (tone) {
  PillTone.SUCCESS -> c.success to c.successTint
  PillTone.WARNING -> c.warning to c.warningTint
  PillTone.DANGER -> c.danger to c.dangerTint
  PillTone.NEUTRAL -> c.muted to c.muted.copy(alpha = NEUTRAL_TINT)
}

/** زمینهٔ نشانِ خنثی: همان رنگِ `muted` با ۱۲٪ — هم‌وزنِ تینت‌های دیگر */
const val NEUTRAL_TINT = 0.12f
