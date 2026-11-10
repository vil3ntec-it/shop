package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  PRD-17 — عکس‌های تکمیلیِ کالا و PRD-27 — عکسِ جدای ویترین، روی بانکِ تصاویرِ دکان (پرچمِ `shop.image_bank`؛
 *  `server/src/routes/shop-images.js`، `server/test/image-bank-extras.test.js`).
 *
 *   • تکمیلی: `POST /shop/images?product_id=&role=` با نقشِ [EXTRA_ROLES]، سپس نسخهٔ نمایش و کوچک (`PUT /:id/v/:v/:size`)،
 *     **بی** `select` — عکسِ اصلیِ کالا و ویترین دست نمی‌خورد. فهرست از `GET /mine?product_id=` (همهٔ صفحه‌ها، [collect])؛ برداشتن از کالا
 *     `POST /:id/detach` (اصل و نسخه‌ها روی سرور می‌مانند).
 *   • ویترین: همان بارگذاری با نقشِ [STOREFRONT_ROLE] و سپس `POST /select {source:'shop'}` — ویترین این عکس را نشان
 *     می‌دهد و عکسِ روی دستگاه (صندوق و فهرست) دست نمی‌خورد. ⛔ عکسِ تازهٔ دستگاه آن را خودکار کنار نمی‌زند
 *     ([keepStorefrontPhoto] در `ImageBank.uploadOwn`)؛ فقط «همان عکسِ کالا در ویترین» ِ صاحب.
 *   • اندازه و نوع همان سرور: نسخهٔ نمایش ≤۱۲۰۰ پیکسل و ≤۶۰۰KB، کوچک ≤۴۰۰ و ≤۱۵۰KB (`DERIV` ِ `lib/image-bank.js`) —
 *     همان `ImagePrep` و `ImageBank.prepare` ِ برنامه (کتابخانهٔ تازه‌ای نیست).
 *
 *  منطقِ خالص (بی `java.*`/`android.*`، §۴۰.۳).
 */
object ProductGallery {

  /** نقش‌های عکسِ تکمیلی — زیرمجموعهٔ `ROLES` ِ سرور؛ اولی پیش‌فرض */
  val EXTRA_ROLES: List<String> = listOf("detail", "back", "label", "variant")
  /** عکسِ جدای ویترین */
  const val STOREFRONT_ROLE = "promo"

  /** سقفِ عکس‌های تکمیلیِ هر کالا در برنامه (سرور فقط سقفِ کلِ دکان را دارد) */
  const val MAX_EXTRAS = 8

  /** همان `DERIV` ِ سرور */
  const val DISPLAY_SIDE = 1200
  const val DISPLAY_MAX_BYTES = 600 * 1024
  const val THUMB_SIDE = 400
  const val THUMB_MAX_BYTES = 150 * 1024
  /** همان سقفِ خواندنِ فایلِ کاربر در ورودِ گروهیِ عکس (`ImageBulk.MAX_FILE_BYTES`) */
  const val MAX_INPUT_BYTES = 40L * 1024 * 1024

  private const val BASE = "/shop/images"

  data class Item(val id: String, val role: String, val version: Int, val thumbUrl: String?, val displayUrl: String?)

  private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content
  private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull?.toInt()

  /** `GET /mine?product_id=` ⇒ عکس‌های همین کالا (نشانی فقط از خودِ بانک، `ImageQuality.filePath`) */
  fun itemsOf(o: JsonObject?): List<Item> = (o?.get("items") as? JsonArray).orEmpty().mapNotNull { e ->
    val r = e as? JsonObject ?: return@mapNotNull null
    val id = r["id"].str() ?: return@mapNotNull null
    val urls = r["urls"] as? JsonObject
    Item(
      id = id,
      role = r["role"].str() ?: "main",
      version = r["version"].int()?.takeIf { it >= 1 } ?: 1,
      thumbUrl = urls?.get("thumb").str()?.takeIf { ImageQuality.filePath(it) != null },
      displayUrl = urls?.get("display").str()?.takeIf { ImageQuality.filePath(it) != null },
    )
  }

  /** عکس‌های تکمیلی (نه اصلی‌های پیشینِ دستگاه، نه عکسِ ویترین) — همان ترتیبِ سرور (تازه‌ترین اول) */
  fun extras(items: List<Item>): List<Item> = items.filter { it.role in EXTRA_ROLES }

  fun canAdd(extras: Int): Boolean = extras < MAX_EXTRAS

  /** همان `PAGE_MAX` ِ `lib/image-bank.js` (سرور بیش از این نمی‌دهد) */
  const val PAGE = 50
  /** سقفِ صفحه‌ها در یک خواندن: کلِ عکس‌های دکان (`MAX_PHOTOS` ِ `lib/product-photos.js`، ۲۰۰۰۰) ÷ [PAGE] */
  const val MAX_PAGES = 400

  /** یک صفحهٔ `GET /mine` (تازه‌ترین اول)؛ صفحهٔ بعد با `cursor` = `next_cursor` ِ همان پاسخ */
  fun minePath(productId: String, cursor: String? = null): String = "$BASE/mine?product_id=" + ImageChanges.encode(productId) +
    "&limit=$PAGE" + (cursor?.let { "&cursor=" + ImageChanges.encode(it) } ?: "")

  /** `next_cursor` ِ پاسخ؛ صفحهٔ آخر ⇒ null */
  fun nextCursorOf(o: JsonObject?): String? = o?.get("next_cursor").str()

  /**
   *  همهٔ عکس‌های کالا، صفحه‌به‌صفحه با `next_cursor` ِ سرور — پیش‌تر فقط صفحهٔ اول (۵۰): کالای با بیش از ۵۰ عکس عکسِ
   *  ویترین را از فهرست می‌انداخت ([storefrontOf] «همان عکسِ کالا» می‌دید) و بارگذاریِ بعدیِ دستگاه ([keepStorefrontPhoto]
   *  ⇐ false) جایش را می‌گرفت. [enough] ⇒ زودتر بس (مثلاً عکسِ نشان‌داده پیدا شد)؛ cursor ِ درجا یا صفحهٔ خالی ⇒ پایان.
   *  [get] پاسخِ یک صفحه (`inline` ⇒ از تابعِ suspend می‌شود `api.get` زد).
   */
  inline fun collect(productId: String, enough: (List<Item>) -> Boolean = { false }, get: (String) -> JsonObject?): List<Item> {
    val all = ArrayList<Item>()
    var cursor: String? = null
    repeat(MAX_PAGES) {
      val page = get(minePath(productId, cursor))
      val items = itemsOf(page)
      all += items
      if (items.isEmpty() || enough(all)) return all
      cursor = nextCursorOf(page)?.takeIf { next -> next != cursor } ?: return all
    }
    return all
  }

  /** نشانیِ بارگذاری؛ نقشِ دیگری هرگز ساخته نمی‌شود */
  fun uploadPath(productId: String, role: String): String {
    require(role in EXTRA_ROLES || role == STOREFRONT_ROLE) { "bad role" }
    return "$BASE?product_id=" + ImageChanges.encode(productId) + "&role=" + role
  }

  /** نسخهٔ کوچکِ ساختهٔ برنامه — فقط `display` و `thumb` */
  fun derivativePath(imageId: String, version: Int, size: String): String {
    require(size == "display" || size == "thumb") { "bad size" }
    return "$BASE/" + ImageChanges.encode(imageId) + "/v/" + version.coerceAtLeast(1) + "/" + size
  }

  fun detachPath(imageId: String): String = "$BASE/" + ImageChanges.encode(imageId) + "/detach"

  const val SELECT_PATH = "$BASE/select"

  /** «این عکس در ویترین» — همان بدنهٔ `ImageBank.uploadOwn` */
  fun selectBody(productId: String, imageId: String, version: Int): JsonObject = buildJsonObject {
    put("product_id", productId); put("source", "shop"); put("image_id", imageId); put("version", version.coerceAtLeast(1))
  }

  /** پاسخِ ۲۰۱ ِ بارگذاری ⇒ (شناسه، نسخه)؛ بدشکل ⇒ null */
  fun uploadedOf(o: JsonObject?): Pair<String, Int>? {
    val id = o?.get("id").str() ?: return null
    return id to (o?.get("version").int()?.takeIf { it >= 1 } ?: 1)
  }

  /** ویترین کدام عکس را نشان می‌دهد */
  enum class Storefront {
    /** همان عکسِ کالا (عکسِ دستگاه، از بانک یا همگام‌سازیِ امروز) */
    DEVICE,
    /** عکسِ جدای ویترین */
    OWN,
    /** عکسِ بانکِ مرکزی */
    CENTRAL,
    /** بی عکس */
    NONE,
  }

  fun storefrontOf(shown: ImageQuality.Shown, items: List<Item>): Storefront = when (shown.source) {
    "shop" -> if (items.any { it.id == shown.imageId && it.role == STOREFRONT_ROLE }) Storefront.OWN else Storefront.DEVICE
    "legacy_photo" -> Storefront.DEVICE
    "central" -> Storefront.CENTRAL
    else -> Storefront.NONE
  }

  /**
   *  عکسِ تازهٔ دستگاه ([uploadingImageId]) جای عکسِ جدای ویترین می‌نشیند؟ نه — مگر صاحب خودش «همان عکسِ کالا در
   *  ویترین» را بزند (`force`). @return true ⇒ گامِ `select` ِ عکسِ دستگاه زده نمی‌شود
   */
  fun keepStorefrontPhoto(shown: ImageQuality.Shown, items: List<Item>, uploadingImageId: String): Boolean =
    shown.source == "shop" && shown.imageId.isNotEmpty() && shown.imageId != uploadingImageId &&
      items.any { it.id == shown.imageId && it.role == STOREFRONT_ROLE }

  enum class Detach {
    /** از کالا برداشته شد (یا پیش‌تر برداشته شده بود) */
    DONE,
    /** همین حالا عکسِ ویترین است ⇒ اول عکسِ دیگری */
    SELECTED,
    /** عکس دیگر نیست ⇒ فهرست از نو */
    GONE,
    /** بانک خاموش یا بی اجازه */
    DENIED,
    /** پاسخی نیامد ⇒ دوباره */
    FAILED,
  }

  fun detachOutcome(ok: Boolean, status: Int, code: String): Detach = when {
    ok -> Detach.DONE
    status == 409 && code == "image_selected" -> Detach.SELECTED
    status == 404 -> Detach.GONE
    status == 403 || status == 400 -> Detach.DENIED
    else -> Detach.FAILED
  }

  /** پاسخِ ناموفقِ خواندن ⇒ «روشن نیست» (پرچمِ خاموش، سرورِ کهنه، بی اجازه)؛ وگرنه گذرا */
  fun off(status: Int): Boolean = status == 403 || status == 404
}
