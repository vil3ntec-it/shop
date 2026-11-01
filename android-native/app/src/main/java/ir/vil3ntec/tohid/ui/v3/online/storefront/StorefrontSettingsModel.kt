package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 *  فرمِ «تنظیمات» ِ ویترین — همان سنجه‌ها و همان بدنهٔ `PUT /shop/storefront` ِ صفحهٔ پیشین
 *  (نشانی، نام، رنگ، دست‌کم یکی از ارسال/دریافت، پول با `StorefrontOwner.parseMinor`، ساعتِ
 *  هر روز با `parseDay`، تعطیلی‌ها با `parseClosedDates`)، بیرون از نمایش و با آزمون
 *  (`StorefrontSettingsModelTest`). خطای هر کادر با کلیدِ همان کادر.
 */
object StorefrontSettingsModel {

  data class Form(
    val slug: String = "",
    val title: String = "",
    val tagline: String = "",
    val phone: String = "",
    val accent: String = "",
    val notice: String = "",
    val enabled: Boolean = false,
    val delivery: Boolean = true,
    val pickup: Boolean = true,
    val fee: String = "",
    val minOrder: String = "",
    val timezone: String = "Asia/Kabul",
    val days: Map<Int, String> = emptyMap(),
    val closed: String = "",
  )

  data class Result(val errors: Map<String, String>, val body: JsonObject?)

  private val ACCENT = Regex("^#[0-9a-fA-F]{6}$")

  fun from(settings: StorefrontSettingsDto?): Form {
    val s = settings ?: StorefrontSettingsDto()
    return Form(
      slug = s.slug,
      title = s.title,
      tagline = s.tagline,
      phone = s.contactPhone.replace(Regex("^\\+93"), "0"),
      accent = s.accent,
      notice = s.notice,
      enabled = s.enabled,
      delivery = s.fulfillment.delivery,
      pickup = s.fulfillment.pickup,
      fee = if (settings == null) "" else StorefrontOwner.formatMinor(s.deliveryFeeMinor),
      minOrder = if (settings == null) "" else StorefrontOwner.formatMinor(s.minOrderMinor),
      timezone = s.timezone,
      days = (0..6).associateWith { d -> StorefrontOwner.formatDay(s.weeklyHours[d.toString()].orEmpty().map { it[0] to it[1] }) },
      closed = s.closedDates.joinToString("، "),
    )
  }

  /** همان `build()` ِ صفحهٔ پیشین: خطاها، یا بدنهٔ ذخیره */
  fun build(f: Form): Result {
    val errors = LinkedHashMap<String, String>()
    StorefrontOwner.slugError(f.slug)?.let { errors["slug"] = it }
    if (f.enabled && f.title.isBlank()) errors["title"] = StrOnline.sfTitleRequired
    if (f.accent.isNotBlank() && !ACCENT.matches(f.accent.trim())) errors["accent"] = StrOnline.sfAccentFormat
    if (!f.delivery && !f.pickup) errors["fulfillment"] = StrOnline.sfOneFulfilment
    val feeMinor = runCatching { StorefrontOwner.parseMinor(f.fee, StorefrontOwner.MAX_FEE_MINOR) ?: 0L }
      .getOrElse { errors["fee"] = it.message ?: StrOnline.badAmount; 0L }
    val minMinor = runCatching { StorefrontOwner.parseMinor(f.minOrder) ?: 0L }
      .getOrElse { errors["min"] = it.message ?: StrOnline.badAmount; 0L }
    val hours = LinkedHashMap<Int, List<Pair<Int, Int>>>()
    for (d in 0..6) {
      val r = StorefrontOwner.parseDay(f.days[d].orEmpty())
      val e = r.error
      if (e != null) errors["day$d"] = e else hours[d] = r.ranges
    }
    val (closedList, closedErr) = StorefrontOwner.parseClosedDates(f.closed)
    closedErr?.let { errors["closed"] = it }
    if (errors.isNotEmpty()) return Result(errors, null)
    return Result(
      emptyMap(),
      buildJsonObject {
        put("slug", f.slug.trim().lowercase())
        put("enabled", f.enabled)
        put("title", f.title.trim())
        put("tagline", f.tagline.trim())
        put("contactPhone", f.phone.trim())
        put("accent", f.accent.trim().lowercase())
        put("notice", f.notice.trim())
        put("timezone", f.timezone.trim())
        put("weeklyHours", StorefrontRepository.weeklyHoursJson(hours))
        put("closedDates", JsonArray(closedList.map { JsonPrimitive(it) }))
        put("acceptDelivery", f.delivery)
        put("acceptPickup", f.pickup)
        put("deliveryFeeMinor", feeMinor)
        put("minOrderMinor", minMinor)
      },
    )
  }

  /** نشانیِ ویترین برای مشتری: «my-shop.vill3n.top» */
  fun address(slug: String): String = "${slug.trim().lowercase().ifBlank { "…" }}.vill3n.top"
}
