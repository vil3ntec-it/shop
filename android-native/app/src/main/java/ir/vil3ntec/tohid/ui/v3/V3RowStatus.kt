package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.ui.theme.V3Status

/*
 *  وضعیتِ آرامِ ردیف (`claude/e-rows-63d`) — دادهٔ خالص، بی Compose، تا مدل‌ها (`HistoryModel`…) آن را آماده بسازند و
 *  ردیف فقط بکشد (`V3AmountStatus`، `V3QuietStatus` در `V3RowBits.kt`).
 */

/**
 *  شدّتِ متنِ وضعیت — رنگ هرگز تنها نیست: نوشته همیشه هست. [DEBT]/[RECEIPT] رنگِ معناییِ دفترِ قرض است («• برد» و
 *  «• قرض‌دار» سرخِ بدهی، «• رسید» سبزِ رسید — همان `LedgerTone`)، نه هشدار (`claude/e-rows-63q`).
 */
enum class V3RowTone { QUIET, ATTENTION, DANGER, DEBT, RECEIPT }

/** وضعیتِ آرامِ یک ردیف («لغوشده»، «نسیه») با شدّتش */
data class V3RowStatus(val text: String, val tone: V3RowTone = V3RowTone.QUIET) {
  companion object {
    /** همان معنای قرصِ §۸، به شدّتِ متن: لغو و خطا قرمز، هشدار و انتظار کهربایی، بقیه آرام */
    fun of(text: String, status: V3Status): V3RowStatus = V3RowStatus(text, toneOf(status))

    fun toneOf(status: V3Status): V3RowTone = when (status) {
      V3Status.CANCELLED -> V3RowTone.DANGER
      V3Status.WARNING, V3Status.AWAITING, V3Status.REVIEW -> V3RowTone.ATTENTION
      else -> V3RowTone.QUIET
    }
  }
}

/** جداکنندهٔ «مبلغ • وضعیت» */
const val V3_ROW_DOT = " • "
