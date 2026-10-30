package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.ShopColors
import ir.vil3ntec.tohid.ui.theme.StatusTone

/**
 *  معنای رنگِ نشان — رنگ هیچ‌جا تزیین نیست. هفت معنا، همان هفت وضعیتِ VILL3N (§۸):
 *
 *  | نشان      | معنا                     | توکن                    |
 *  |-----------|--------------------------|-------------------------|
 *  | SUCCESS   | تحویل‌شده، موفق، فعال    | `status.delivered`       |
 *  | INFO      | در مسیر، تازه، اطلاع     | `status.inDelivery`      |
 *  | WARNING   | در انتظارِ تأیید، کم     | `status.awaitingApproval`|
 *  | DANGER    | لغو، رد، تمام‌شده        | `status.cancelled`       |
 *  | REVIEW    | نیاز به بررسی            | `status.needsReview`     |
 *  | PREPARING | در آماده‌سازی            | `status.preparing`       |
 *  | NEUTRAL   | غیرفعال، بسته، خنثی      | `status.inactive`        |
 */
enum class PillTone { SUCCESS, WARNING, DANGER, NEUTRAL, INFO, REVIEW, PREPARING }

/** توکنِ وضعیتِ هر نشان */
fun statusTone(c: ShopColors, tone: PillTone): StatusTone = when (tone) {
  PillTone.SUCCESS -> c.status.delivered
  PillTone.INFO -> c.status.inDelivery
  PillTone.WARNING -> c.status.awaitingApproval
  PillTone.DANGER -> c.status.cancelled
  PillTone.REVIEW -> c.status.needsReview
  PillTone.PREPARING -> c.status.preparing
  PillTone.NEUTRAL -> c.status.inactive
}

/**
 *  رنگِ متن/نقطه و زمینهٔ هر نشان. متن روی زمینهٔ کم‌رنگِ همان رنگ می‌نشیند؛
 *  همهٔ جفت‌ها در `BackOfficeContrastTest` ≥ ۴٫۵:۱ سنجیده می‌شوند.
 */
fun pillColors(c: ShopColors, tone: PillTone): Pair<Color, Color> = statusTone(c, tone).let { it.fg to it.bg }

/** زمینهٔ شمارِ خنثی (زبانه‌ها): همان رنگِ `muted` با ۱۲٪ */
const val NEUTRAL_TINT = 0.12f
