package ir.vil3ntec.tohid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.JpegMeta
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder

/**
 *  بانکِ تصاویر — سمتِ برنامه (گوشی و کامپیوتر؛ v3 ۳۴، ۳۶.۸، ۳۷.۵ تا ۳۷.۷).
 *  سرور: `server/src/routes/shop-images.js`؛ همتای وب: `license/image-bank.js`.
 *
 *  ⛔ پرچمِ `shop.image_bank` خاموش ⇒ `/config` ۴۰۳ است، `enabled` = false و
 *     هیچ درخواستِ دیگری به بانک نمی‌رود؛ عکسِ کالا همان راهِ امروز را می‌رود
 *     (`PhotoStore` + `PhotoSync`).
 *  ⛔ قیمت، موجودی، قفسه و SKUِ دکان از بانک کپی نمی‌شود — فقط عکس.
 *  ⛔ هیچ قابلیتِ هوشِ مصنوعی این‌جا نیست (BLOCKED-EXTERNAL).
 */
object ImageBank {

  /** null = هنوز پرسیده نشده؛ دکمهٔ بانک فقط با `true` دیده می‌شود */
  val enabled = mutableStateOf<Boolean?>(null)

  private const val BASE = "/shop/images"
  private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

  fun transient(f: Throwable): Boolean =
    f is ApiFailure && (f.retryable || f is ApiFailure.RateLimited || f is ApiFailure.ServerFault)

  /** `/config`: ۲۰۰ ⇒ روشن؛ ۴۰۳/۴۰۴/بی‌حساب ⇒ خاموش؛ بی اینترنت ⇒ آخرین حالِ دانسته */
  suspend fun refresh(context: Context): Boolean {
    if (!Backend.isReady(context)) { enabled.value = false; return false }
    return try {
      val c = Backend.api(context).get("$BASE/config")
      val on = c["enabled"]?.jsonPrimitive?.booleanOrNull == true
      enabled.value = on
      if (on) runCatching { drain(context) }
      on
    } catch (f: Throwable) {
      if (!transient(f)) enabled.value = false
      enabled.value == true
    }
  }

  /* ------------------------------ جست‌وجو ------------------------------ */

  data class Img(val id: String, val version: Int, val role: String, val thumb: String?, val display: String?)
  data class Hit(
    val productId: String, val name: String, val brand: String, val variant: String,
    val size: String, val packCount: Int, val packaging: String, val gtin: String, val altNames: List<String>,
    /** «دقیق» فقط برای بارکد/SKUِ دقیق — همان `certain`ِ سرور */
    val certain: Boolean, val kind: String, val reasons: List<String>,
    val ambiguous: Boolean, val distinguish: List<String>, val images: List<Img>,
  )
  data class Page(val items: List<Hit>, val next: String?, val barcodeValid: Boolean?)

  val KIND = mapOf(
    "barcode_exact" to "بارکدِ دقیق", "sku_exact" to "SKUِ دقیق", "name_exact" to "نامِ کاملِ یکسان",
    "name_words" to "همهٔ کلمه‌های نام", "spelling_tolerant" to "املای نزدیک",
  )
  val DIFF = mapOf(
    "brand" to "برند", "variant" to "طعم/مدل", "size" to "وزن/حجم", "pack_count" to "تعداد در بسته", "packaging" to "بسته‌بندی",
  )

  private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
  private fun JsonObject.list(k: String) = (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()

  suspend fun search(context: Context, q: String, hasImage: Boolean, cursor: String? = null): Page {
    val text = q.trim()
    val digits = ir.vil3ntec.tohid.core.image.BulkMatch.digitsOf(text)
    val params = StringBuilder("q=").append(enc(text))
    if (digits.length >= 8 && digits.length == text.replace(Regex("[\\s-]"), "").length) params.append("&barcode=").append(digits)
    if (hasImage) params.append("&has_image=1")
    if (cursor != null) params.append("&cursor=").append(enc(cursor))
    val r = Backend.api(context).get("$BASE/search?$params")
    val items = (r["items"] as? JsonArray).orEmpty().map { e ->
      val o = e.jsonObject
      val p = o["product"]?.jsonObject ?: JsonObject(emptyMap())
      val m = o["match"]?.jsonObject ?: JsonObject(emptyMap())
      val sizeText = listOf(p.s("size_value"), p.s("size_unit")).filter { it.isNotBlank() }.joinToString(" ")
      Hit(
        productId = p.s("id"), name = p.s("name"), brand = p.s("brand"), variant = p.s("variant"),
        size = sizeText, packCount = p["pack_count"]?.jsonPrimitive?.intOrNull ?: 0, packaging = p.s("packaging"),
        gtin = p.s("gtin"), altNames = p.list("alt_names"),
        certain = m["certain"]?.jsonPrimitive?.booleanOrNull == true, kind = m.s("kind"), reasons = m.list("reasons"),
        ambiguous = o["ambiguous"]?.jsonPrimitive?.booleanOrNull == true, distinguish = o.list("distinguish"),
        images = (o["images"] as? JsonArray).orEmpty().map { ie ->
          val i = ie.jsonObject
          val u = i["urls"]?.jsonObject ?: JsonObject(emptyMap())
          Img(i.s("id"), i["version"]?.jsonPrimitive?.intOrNull ?: 1, i.s("role"),
            u["thumb"]?.jsonPrimitive?.contentOrNull, u["display"]?.jsonPrimitive?.contentOrNull)
        },
      )
    }
    return Page(items, r["next_cursor"]?.jsonPrimitive?.contentOrNull, r["barcode_valid"]?.jsonPrimitive?.booleanOrNull)
  }

  /** نشانیِ فایلی که سرور داد (`/api/shop/images/file/…`) ⇒ بایت‌ها با نشستِ کاربر */
  suspend fun file(context: Context, url: String): ByteArray {
    val path = url.removePrefix("/api/v1").removePrefix("/api")
    require(path.startsWith("$BASE/file/")) { "نشانیِ عکس درست نیست" }
    return Backend.api(context).getBytes(path)
  }

  /**
   *  آیا تیکِ «همین کالای من است» لازم است؟ همان قاعدهٔ `classify`ِ سرور؛
   *  سرور باز خودش می‌سنجد. بارکدِ متفاوت ⇒ `block`.
   */
  data class Assess(val block: String, val needsConfirm: Boolean, val why: List<String>)

  fun assess(hit: Hit, name: String, barcode: String): Assess {
    val bm = ir.vil3ntec.tohid.core.image.BulkMatch
    val rowG = bm.gtin14(barcode)
    val bankG = bm.gtin14(hit.gtin)
    if (rowG.isNotEmpty() && bankG.isNotEmpty() && rowG != bankG) {
      return Assess("بارکدِ این کالا ($barcode) با بارکدِ کالای بانک فرق دارد", false, emptyList())
    }
    val key = ir.vil3ntec.tohid.core.text.TextNormalizer::compareKey
    val rowName = key(name)
    val sameBarcode = rowG.isNotEmpty() && rowG == bankG
    val nameDiffers = rowName.isNotEmpty() && (listOf(hit.name) + hit.altNames).none { key(it) == rowName } && !sameBarcode
    val certain = hit.certain && !hit.ambiguous
    val why = ArrayList<String>()
    if (!hit.certain) why += "تطبیق فقط با ${KIND[hit.kind] ?: "نام"} است، نه بارکد"
    if (hit.ambiguous) why += "چند نوعِ مشابه هست؛ فرق در: " + hit.distinguish.joinToString("، ") { DIFF[it] ?: it }
    if (nameDiffers) why += "نامِ کالای شما با نامِ کالای بانک یکی نیست"
    val needs = !certain || nameDiffers || bankG.isEmpty()
    if (needs && why.isEmpty()) why += "بارکدِ معتبری برای سنجش نیست؛ تطبیق را خودتان تأیید کنید"
    return Assess("", needs, why)
  }

  /**
   *  ثبتِ عکسِ مرکزی برای کالای همین دکان، بعد گذاشتنِ نسخهٔ نمایش روی دستگاه.
   *  خطای سرور (`needs_confirmation`، `barcode_mismatch`) همان‌طور بالا می‌رود.
   */
  suspend fun chooseCentral(
    context: Context, productId: String, img: Img, name: String, barcode: String, confirm: Boolean,
  ): ByteArray {
    val api = Backend.api(context)
    val bytes = file(context, img.display ?: img.thumb ?: error("این عکس نسخهٔ نمایش ندارد"))
    api.post("$BASE/select", buildJsonObject {
      put("product_id", productId); put("source", "central"); put("image_id", img.id); put("version", img.version)
      put("expect", buildJsonObject { put("barcode", barcode); put("name", name) })
      if (confirm) put("confirm", true)
    })
    return bytes
  }

  /* ------------------------- آماده‌سازی روی دستگاه ------------------------- */

  class Prepared(val display: ByteArray, val thumb: ByteArray?, val width: Int, val height: Int, val small: Boolean)

  /** آلفا روی زمینهٔ سفید — PNGِ شفاف در JPEG سیاه نشود */
  private fun onWhite(c: Int): Int {
    val a = c ushr 24
    if (a == 255) return c
    fun ch(v: Int) = (v * a + 255 * (255 - a)) / 255
    return (0xFF shl 24) or (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
  }

  /**
   *  بایت‌های فایلِ کاربر ⇒ نمایش (≤۱۲۰۰، ≤۶۰۰KB) و کوچک (≤۴۰۰، ≤۱۵۰KB)؛ چرخشِ
   *  EXIF روی پیکسل‌ها و بی هیچ فرادادهٔ EXIF/GPS (`JpegMeta.strip`). روی نخِ پس‌زمینه.
   */
  suspend fun prepare(bytes: ByteArray): Prepared = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, bounds)
    val w0 = bounds.outWidth
    val h0 = bounds.outHeight
    require(w0 > 0 && h0 > 0) { "این فایل عکس نیست یا خراب است" }
    require(!ImagePrep.tooManyPixels(w0, h0)) { "عکس بیش از اندازه بزرگ است (بیش از ۶۰ مگاپیکسل)" }
    val orientation = if (JpegMeta.isJpeg(bytes)) JpegMeta.orientation(bytes) else 1
    val opts = BitmapFactory.Options().apply { inSampleSize = ImagePrep.sampleSize(w0, h0, ImagePrep.DISPLAY_SIDE) }
    val decoded = BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, opts)
      ?: throw IllegalArgumentException("این فایل عکس نیست یا خراب است")
    val fit = ImagePrep.fit(decoded.width, decoded.height, ImagePrep.DISPLAY_SIDE)
    val scaled = if (fit.w != decoded.width || fit.h != decoded.height) Bitmap.createScaledBitmap(decoded, fit.w, fit.h, true) else decoded
    if (scaled !== decoded) decoded.recycle()
    val px = IntArray(scaled.width * scaled.height)
    scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    for (i in px.indices) px[i] = onWhite(px[i])
    val (opx, osz) = ImagePrep.orient(px, scaled.width, scaled.height, orientation)
    scaled.recycle()
    val upright = Bitmap.createBitmap(opx, osz.w, osz.h, Bitmap.Config.ARGB_8888)
    fun encode(s: ImagePrep.Size, q: Int): ByteArray {
      val b = if (s.w == upright.width && s.h == upright.height) upright else Bitmap.createScaledBitmap(upright, s.w, s.h, true)
      val out = ByteArrayOutputStream()
      b.compress(Bitmap.CompressFormat.JPEG, q, out)
      if (b !== upright) b.recycle()
      return JpegMeta.strip(out.toByteArray())
    }
    val (display, dsz) = ImagePrep.encodeUnder(osz, ImagePrep.DISPLAY_BYTES, ::encode)
    val (thumb, tsz) = ImagePrep.encodeUnder(ImagePrep.fit(dsz.w, dsz.h, ImagePrep.THUMB_SIDE), ImagePrep.THUMB_BYTES, ::encode)
    upright.recycle()
    Prepared(
      display, thumb.takeIf { ImagePrep.aspectOk(tsz, dsz) }, dsz.w, dsz.h,
      small = maxOf(w0, h0) < ImagePrep.MIN_SIDE,
    )
  }

  /**
   *  عکسِ خودِ دکان روی بانک: اصل ← نسخهٔ نمایش ← کوچک ← انتخاب به‌عنوانِ عکسِ
   *  این کالا. `up` پیشرفت است و پس از هر قدم با `onStep` ذخیره می‌شود.
   */
  suspend fun uploadOwn(context: Context, productId: String, prep: Prepared, up: BulkJob.Upload, onStep: () -> Unit = {}) {
    val api = Backend.api(context)
    if (up.imageId.isEmpty()) {
      val r = api.postBytes("$BASE?product_id=${enc(productId)}&role=main", prep.display, "image/jpeg")
      up.imageId = r["id"]?.jsonPrimitive?.contentOrNull ?: throw ApiFailure.InvalidResponse()
      up.version = r["version"]?.jsonPrimitive?.intOrNull ?: 1
      onStep()
    }
    val base = "$BASE/${enc(up.imageId)}/v/${up.version}"
    if (!up.display) { api.putBytes("$base/display", prep.display, "image/jpeg"); up.display = true; onStep() }
    if (prep.thumb != null && !up.thumb) { api.putBytes("$base/thumb", prep.thumb, "image/jpeg"); up.thumb = true; onStep() }
    if (!up.selected) {
      api.post("$BASE/select", buildJsonObject {
        put("product_id", productId); put("source", "shop"); put("image_id", up.imageId); put("version", up.version)
      })
      up.selected = true; onStep()
    }
  }

  /* ------------- صفِ عکسِ خودِ دکان (پس از گذاشتنِ عکس، آفلاین‌پذیر) ------------- */

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-imgbank", 0)
  private val lock = Mutex()

  /** عکسِ تازهٔ کالا (از استودیو) با بانکِ روشن ⇒ در صف برای بانکِ همین دکان */
  fun enqueueOwn(context: Context, productId: String) {
    if (enabled.value != true) return
    synchronized(this) {
      val p = prefs(context)
      val set = (p.getStringSet("own", emptySet()) ?: emptySet()).toMutableSet()
      set += productId
      p.edit().putStringSet("own", set).remove("up:$productId").apply()
    }
  }

  suspend fun drain(context: Context) = lock.withLock {
    if (enabled.value != true) return@withLock
    val p = prefs(context)
    for (id in (p.getStringSet("own", emptySet()) ?: emptySet()).toList()) {
      val done = {
        synchronized(this) {
          val set = (p.getStringSet("own", emptySet()) ?: emptySet()).toMutableSet()
          set -= id
          p.edit().putStringSet("own", set).remove("up:$id").apply()
        }
      }
      val bytes = withContext(Dispatchers.IO) { PhotoStore.bytes(context, id) }
      if (bytes == null) { done(); continue }
      val saved = p.getString("up:$id", null)?.split('|')
      val up = BulkJob.Upload(
        imageId = saved?.getOrNull(0).orEmpty(), version = saved?.getOrNull(1)?.toIntOrNull() ?: 1,
        display = saved?.getOrNull(2) == "1", thumb = saved?.getOrNull(3) == "1", selected = saved?.getOrNull(4) == "1",
      )
      try {
        val prep = prepare(bytes)
        uploadOwn(context, id, prep, up) {
          p.edit().putString("up:$id", listOf(up.imageId, up.version, if (up.display) 1 else 0, if (up.thumb) 1 else 0, if (up.selected) 1 else 0).joinToString("|")).apply()
        }
        done()
      } catch (f: Throwable) {
        if (transient(f)) return@withLock
        done()   //  عکس روی دستگاه و همگام‌سازیِ امروز سرِ جایش است؛ فقط بانک نپذیرفت
      }
    }
  }
}
