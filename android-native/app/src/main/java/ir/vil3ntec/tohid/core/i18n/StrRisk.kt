package ir.vil3ntec.tohid.core.i18n

/**
 *  هشدارِ رفتارِ غیرعادیِ صندوقدار (RSK-10) در زبانهٔ «ضدتقلب» ِ گزارشِ تازه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطقِ هشدار در `core/risk/CashierAlerts` (commonMain).
 */
@Suppress("unused")
object StrRisk {
  fun title(n: Any): String = t(
    "$n هشدارِ رفتارِ غیرعادیِ صندوقدار",
    "$n unusual cashier ${pl(n, "alert", "alerts")}",
    "د صندوقدار $n غیرعادي ${pl(n, "خبرتیا", "خبرتیاوې")}",
  )
  val rule: String get() = t(
    "سنجش با بقیهٔ کاربرانِ همین بازه: دستِ کم ۳ بار و دو برابرِ نرخِ آن‌ها",
    "Compared with the other users in this period: at least 3 times and twice their rate",
    "په همدې موده کې د نورو کاروونکو سره پرتله: لږ تر لږه ۳ ځلې او د هغوی دوه چنده",
  )
  val voids: String get() = t("لغوِ فاکتور", "voided invoices", "لغوه شوي بلونه")
  val discounts: String get() = t("تخفیف", "discounts", "تخفیفونه")
  val noSales: String get() = t("کشو بی فروش", "no-sale drawer opens", "بې پلور د دراز خلاصول")
  fun line(name: Any, kind: Any, count: Any, invoices: Any, peerCount: Any, peerInvoices: Any): String = t(
    "$name: $count $kind در $invoices فاکتور — بقیه $peerCount در $peerInvoices",
    "$name: $count $kind in $invoices invoices — others $peerCount in $peerInvoices",
    "$name: $count $kind په $invoices بلونو کې — نور $peerCount په $peerInvoices کې",
  )
}
