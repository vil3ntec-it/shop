package ir.vil3ntec.tohid.ui.v3.sales.draft

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText

/**
 *  متنِ برگهٔ «فاکتورِ نیمه‌کاره» (POS-13) — همان سه جملهٔ `DraftRecoveryPrompt` ِ پیشین:
 *  چند قلم و چه مبلغ، ردیف‌هایی که کالایشان رفته (اگر بود)، و «هنوز ثبت نشده است».
 *  تصمیمِ «پیشنهاد/ثبت‌شده/هیچ» با خودِ موتور است (`SaleDraftRules.decide`).
 */
object DraftModel {
  fun body(lines: Int, total: Double, dropped: Int): String = listOfNotNull(
    StrSale.draftBody(plain(lines), PhoneSaleText.amount(total)),
    if (dropped > 0) StrSale.draftDropped(plain(dropped)) else null,
    StrSale.draftNotPosted,
  ).joinToString(" ")
}
