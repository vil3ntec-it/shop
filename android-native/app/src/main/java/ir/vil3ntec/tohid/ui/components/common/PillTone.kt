package ir.vil3ntec.tohid.ui.components.common

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.ShopColors
import ir.vil3ntec.tohid.ui.theme.StatusTone

/**
 *  معنای رنگِ نشان — رنگ هیچ‌جا تزیین نیست (VILL3N §۸، THEME-V2 «رنگ‌های معنایی»):
 *
 *  | نشان      | معنا                                   | توکن                     |
 *  |-----------|----------------------------------------|--------------------------|
 *  | SUCCESS   | تحویل‌شده، موفق، فعال                  | `status.delivered`        |
 *  | INFO      | در مسیر، تازه، اطلاع                   | `status.inDelivery`       |
 *  | AWAITING  | در انتظارِ تأیید (سفارشِ تازه، درخواست) | `status.awaitingApproval` |
 *  | WARNING   | هشدار: کم، سقف، اختلاف، زمان‌دار       | `status.warning`          |
 *  | DANGER    | لغو، رد، تمام‌شده                      | `status.cancelled`        |
 *  | REVIEW    | نیاز به بررسی                          | `status.needsReview`      |
 *  | PREPARING | در آماده‌سازی                          | `status.preparing`        |
 *  | NEUTRAL   | غیرفعال، بسته، خنثی                    | `status.inactive`         |
 *
 *  AWAITING و REVIEW هر دو بنفش‌اند (هشت معنا، هفت رنگ).
 */
enum class PillTone { SUCCESS, WARNING, DANGER, NEUTRAL, INFO, REVIEW, PREPARING, AWAITING }

/** توکنِ وضعیتِ هر نشان */
fun statusTone(c: ShopColors, tone: PillTone): StatusTone = when (tone) {
  PillTone.SUCCESS -> c.status.delivered
  PillTone.INFO -> c.status.inDelivery
  PillTone.AWAITING -> c.status.awaitingApproval
  PillTone.WARNING -> c.status.warning
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
