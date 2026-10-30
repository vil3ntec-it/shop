package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.ShopData

/**
 *  «ادغامِ تکراری‌ها» (`debtors/merge`، CRM-10) — خالص، بی Compose. گروه‌ها و پیش‌نمایش همان
 *  `CustomerMerge` اند؛ این‌جا فقط تصمیمِ نمایش است.
 *
 *  - فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، پیش‌فرض **خاموش**): خاموش ⇒ نه
 *    ورودی در فهرست، نه برگه — رفتارِ دکان همان امروز است.
 *  - گروهی که یکی از اعضایش در «حذف با برگرداندن» است (`MoneyUndo.hiddenDebtors`) نشان داده
 *    نمی‌شود تا ادغام روی ردیفی که دارد حذف می‌شود ننشیند.
 */
internal object DebtorMergeModel {

  fun groups(d: ShopData, on: Boolean, hidden: Set<String>): List<CustomerMerge.Group> =
    if (!on) emptyList() else CustomerMerge.duplicates(d).filter { g -> g.members.none { it.id in hidden } }

  /** پیش‌نمایشِ برگهٔ تأیید؛ null ⇐ دیگر شدنی نیست (یکی پیش‌تر ادغام یا حذف شد) */
  fun preview(d: ShopData, keepId: String, dropId: String): CustomerMerge.Preview? =
    (CustomerMerge.preview(d, keepId, dropId) as? CustomerMerge.Result.Ok)?.preview
}
