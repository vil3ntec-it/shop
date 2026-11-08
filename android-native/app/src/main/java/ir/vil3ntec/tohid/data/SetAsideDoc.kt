package ir.vil3ntec.tohid.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

//  مدلِ سندِ کنارگذاشته، کنارِ Models.kt (همان بسته) تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)

/**
 *  یک سندِ ردشده که صاحب/مدیر از صندوقِ سندهای ردشده «کنار گذاشت» یا «اصلاح کرد» — فقط روی دستگاهِ سازنده
 *  (`ShopData.setAside`، `data/SetAside.kt`، `core/sync/RejectedSettle`). `id` همان ردیفِ صندوقِ سرور است.
 *   • `ops`: opهای ردشدهٔ همان سند، کامل (همان که به سرور رفت)؛
 *   • `rows`: ردیف‌هایی که از دفتر بیرون آمدند (نامِ مجموعه ⇒ ردیف‌های کامل) — نه در موجودی و قرض و دخل و گزارش،
 *     نه هرگز دوباره فرستاده، و نه پاک؛
 *   • `undo`: opهای برگرداننده‌ای که روی دفتر و سایه نشستند (هیچ‌کدام به سرور نرفت)؛
 *   • `kept`: آنچه برگردانده نشد (`"<جدول>/<شناسه>.<فیلد>"`).
 */
@Serializable
data class SetAsideDoc(
  val id: String,
  val status: String,
  val note: String = "",
  val reason: String = "",
  val fixedDoc: String = "",
  val table: String = "",
  val rowId: String = "",
  val saleId: String = "",
  val resolvedAt: Long = 0,
  val settledAt: Long = 0,
  val ops: List<JsonObject> = emptyList(),
  val rows: JsonObject = JsonObject(emptyMap()),
  val undo: List<JsonObject> = emptyList(),
  val kept: List<String> = emptyList(),
)
