package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.StrSupportAccess
import ir.vil3ntec.tohid.data.repo.SupportGrantDto

/**
 *  TEN-04 — قاعده‌های نوارِ «پشتیبانی در حالِ مشاهده» (`SupportAccessNotice`)، بی Compose. وضعیتِ هر اجازه را سرور می‌دهد؛
 *  این‌جا فقط ساعتِ همین لحظه آن را از کهنگیِ میانِ دو پرسش نگه می‌دارد (اجازه‌ای که همین حالا تمام شد دیگر «در حالِ
 *  مشاهده» نیست، درخواستی که گذشت دیگر پاسخ نمی‌خواهد). دسترسیِ واقعی را فقط سرور باز و بسته می‌کند.
 */
object SupportAccessModel {

  fun live(g: SupportGrantDto, now: Long): Boolean = g.status == "active" && (g.expiresAt ?: 0L) > now
  fun waiting(g: SupportGrantDto, now: Long): Boolean = g.status == "pending" && (g.requestExpiresAt ?: Long.MAX_VALUE) > now

  /** اجازه‌های زنده، زودپایان‌ترین اول */
  fun active(list: List<SupportGrantDto>, now: Long): List<SupportGrantDto> = list.filter { live(it, now) }.sortedBy { it.expiresAt ?: 0L }

  /** درخواست‌های بی‌پاسخ، تازه‌ترین اول */
  fun pending(list: List<SupportGrantDto>, now: Long): List<SupportGrantDto> = list.filter { waiting(it, now) }.sortedByDescending { it.createdAt }

  /** «شکستنِ شیشه» را دکان نمی‌بندد (سرور: ۴۰۹ `break_glass_owner`) ⇒ دکمه‌ای هم نیست */
  fun canRevoke(g: SupportGrantDto): Boolean = g.consent == "tenant"

  /** دقیقه‌های مانده، گردشده به بالا (۱ تا …) — نوار هرگز «۰ دقیقه» نمی‌گوید وقتی هنوز باز است */
  fun minutesLeft(g: SupportGrantDto, now: Long): Long = maxOf(1L, ((g.expiresAt ?: now) - now + 59_999L) / 60_000L)

  /** پرسیدنِ دوباره: با اجازهٔ زنده یا درخواستِ باز هر دقیقه، وگرنه هر پنج دقیقه */
  fun pollMs(list: List<SupportGrantDto>, now: Long): Long = if (active(list, now).isNotEmpty() || pending(list, now).isNotEmpty()) 60_000L else 5 * 60_000L

  fun scope(s: String): String = when (s) {
    "read_private" -> StrSupportAccess.scopeReadPrivate
    "export" -> StrSupportAccess.scopeExport
    "restore" -> StrSupportAccess.scopeRestore
    "view_as" -> StrSupportAccess.scopeViewAs
    else -> s
  }
}
