package ir.vil3ntec.tohid.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 *  نشانیِ تحویلِ مشتریِ دکان (`Debtor.deliveryAddress`، CRM-09، §۴.۴): «نشانه + شماره»؛ دو شماره همان `phone`/`phone2`.
 *  نوشتن فقط از `CustomerCrm.setDeliveryAddress`. `null` = بی نشانی و ⛔ نوشته نمی‌شود (`EncodeDefault.NEVER`): قرض‌دارِ
 *  قدیمی بایت‌به‌بایت همان. Sync v1 آن را یک فیلد می‌فرستد؛ ساختِ پیشین کلید را نمی‌شناسد و پس از A00 پاکش نمی‌کند
 *  (`LedgerDiff.changedFields`، `server/src/lib/newer-fields.js`).
 *  هر بخش اختیاری؛ بخشِ خالی نوشته نمی‌شود. همهٔ بخش‌ها خالی ⇒ خودِ نشانی `null`.
 *  `area` = ولایت، ناحیه، گذر یا کوچه؛ `landmark` = نزدیک‌ترین نشانه (مسجد، مکتب، دکان، چوک)؛ `gate` = رنگِ دروازه و
 *  منزل؛ `floor` = طبقه؛ `plusCode` = Plus Code (بزرگ)؛ `postalCode` = فقط رقم.
 */
@Serializable
data class DeliveryAddress(
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val area: String = "",
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val landmark: String = "",
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val gate: String = "",
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val floor: String = "",
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val plusCode: String = "",
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val postalCode: String = "",
)

/**
 *  پینِ نشانیِ تحویلِ مشتریِ دکان (`Debtor.deliveryPin`، CRM-09، §۴.۴: «پین منبعِ اصلی»): شش رقمِ اعشار با همان گردِ سرور
 *  (`AddressRules.round6`)، در بازه و نه (۰، ۰). نوشتن فقط از `CustomerCrm.setDeliveryPin`؛ از مختصات یا پیوندِ برنامهٔ نقشه
 *  (`core/shopper/PinText`، بی شبکه و بی ژئوکدر). ⛔ فیلدی جدا از [DeliveryAddress] (نه بخشی درونِ آن): ساختِ پیشین که
 *  نشانی را ویرایش کند همان شیء را بی بخشِ ناشناخته بازمی‌نویسد، ولی فیلدِ ناشناختهٔ قرض‌دار را پاک نمی‌کند (A00).
 */
@Serializable
data class DeliveryPin(val lat: Double, val lng: Double)
