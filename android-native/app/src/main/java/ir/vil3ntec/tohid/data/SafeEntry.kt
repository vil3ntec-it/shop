package ir.vil3ntec.tohid.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

//  مدلِ ردیفِ گاوصندوق، کنارِ Models.kt (همان بسته) تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)

/**
 *  یک ردیفِ گاوصندوق (PAY-18، طرحِ SAFE-V1) — دفترِ فقط‌افزودنی؛ قاعده‌ها در هستهٔ
 *  `core/safe/SafeCore` و سنجشِ سرور در `server/src/lib/safe-ledger.js`.
 *  مبلغ عددِ صحیح به یک‌صدمِ افغانی (`amountMinor`)، نه `Double`ِ بقیهٔ دفتر.
 *  `kind`: `count` | `in` | `out` | `from_drawer` | `to_drawer` | `to_bank`.
 *  پیش‌فرض‌ها فقط برای این‌اند که ردیفِ ناقص خواندنِ کلِ دفتر را نشکند؛ هسته آن را کنار می‌گذارد.
 *  فیلدهای اختیاری `null` نوشته نمی‌شوند — مثلِ `Sale.series`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SafeEntry(
  val id: String,
  val kind: String = "",
  val amountMinor: Long = 0,
  val createdAt: Long = 0,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val by: String? = null,
  /** ورود: `deposit`/`other`؛ خروج: `owner_withdrawal`/`petty_expense`/`other`؛ شمارش: دلیلِ اختلاف */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val reason: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val note: String? = null,
  /** شمارهٔ کیسه — برای `to_bank` لازم */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val bagNo: String? = null,
  /** فقط شمارش: مانده‌ای که دستگاهِ شمارنده دید — اختلاف از همین پیداست */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val expectedMinor: Long? = null,
  /** فقط `from_drawer`/`to_drawer`: شناسهٔ `shift_open` و ردیفِ جفتِ `cash_out`/`cash_in` در `auditLog` */
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val shiftId: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val auditId: String? = null,
)
