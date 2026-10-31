package ir.vil3ntec.tohid.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.image.BankPhotoCard
import ir.vil3ntec.tohid.core.image.BulkJob
import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.JpegMeta
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.security.MessageDigest

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
      if (on) try { drain(context) } catch (c: CancellationException) { throw c } catch (_: Exception) {}
      on
    } catch (c: CancellationException) {
      throw c
    } catch (f: Exception) {
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

  /* --------------- منبعِ عکسِ کالا (۳۷.۷؛ IMG-BANK-08، ۰۹، ۱۶) --------------- */

  /** `GET /products/:id` + پیشنهادهای عکسِ خودِ دکان (`/mine`) ⇒ کارتِ منبع (`BankPhotoCard`ِ core) */
  data class SourceState(val resolved: BankPhotoCard.Resolved, val proposals: List<BankPhotoCard.Proposal>)

  suspend fun source(context: Context, productId: String): SourceState {
    val api = Backend.api(context)
    val r = api.get("$BASE/products/${enc(productId)}")
    val central = r["central"] as? JsonObject
    val resolved = BankPhotoCard.Resolved(
      source = BankPhotoCard.sourceOf(r["source"]?.jsonPrimitive?.contentOrNull),
      imageId = r.s("image_id"),
      version = r["version"]?.jsonPrimitive?.intOrNull ?: 0,
      archived = r["archived"]?.jsonPrimitive?.booleanOrNull == true,
      updateAvailable = r["update_available"]?.jsonPrimitive?.intOrNull ?: 0,
      centralImageId = central?.s("image_id").orEmpty(),
      centralVersion = central?.get("version")?.jsonPrimitive?.intOrNull ?: 0,
      userConfirmed = r["confirmed_by_user"]?.jsonPrimitive?.booleanOrNull == true,
    )
    if (resolved.source != BankPhotoCard.Source.SHOP || resolved.imageId.isEmpty()) return SourceState(resolved, emptyList())
    val mine = api.get("$BASE/mine?product_id=${enc(productId)}")
    val proposals = (mine["items"] as? JsonArray).orEmpty()
      .map { it.jsonObject }
      .filter { it.s("id") == resolved.imageId }
      .flatMap { (it["proposals"] as? JsonArray).orEmpty() }
      .map { p -> p.jsonObject.let { BankPhotoCard.Proposal(it.s("id"), it.s("status")) } }
    return SourceState(resolved, proposals)
  }

  /** نسخهٔ نمایشِ عکسی که حالا برای این کالا ثبت است — همان که ویترین نشان می‌دهد */
  private suspend fun shownBytes(context: Context, productId: String): ByteArray {
    val r = Backend.api(context).get("$BASE/products/${enc(productId)}")
    val urls = r["urls"] as? JsonObject
    val url = urls?.get("display")?.jsonPrimitive?.contentOrNull ?: urls?.get("thumb")?.jsonPrimitive?.contentOrNull
      ?: error("این عکس هنوز نسخهٔ نمایش ندارد")
    return file(context, url)
  }

  /**
   *  کارِ کارتِ منبع روی سرور، بعد بایت‌های عکسی که حالا ثبت است (برای گذاشتن روی دستگاه).
   *  بازگشت/نسخه: `select`؛ سرور باز تطبیق و تأییدِ نسخه را می‌سنجد (`needs_confirmation`،
   *  `version_not_approved`، `image_not_selectable` همان‌طور بالا می‌روند).
   */
  suspend fun applySource(context: Context, productId: String, action: BankPhotoCard.Action, name: String, barcode: String): ByteArray? {
    val api = Backend.api(context)
    when (action) {
      is BankPhotoCard.Action.RevertToCentral ->
        api.post("$BASE/select", buildJsonObject { put("product_id", productId); put("source", "central") })
      is BankPhotoCard.Action.UseCentralVersion -> api.post("$BASE/select", buildJsonObject {
        put("product_id", productId); put("source", "central"); put("image_id", action.imageId); put("version", action.version)
        put("expect", buildJsonObject { put("barcode", barcode); put("name", name) })
        if (action.confirm) put("confirm", true)
      })
      is BankPhotoCard.Action.Propose -> {
        //  ⛔ فقط با تیکِ اجازهٔ صریح در رابط صدا زده می‌شود؛ اصلِ عکس خصوصی می‌ماند
        api.post("$BASE/${enc(action.imageId)}/propose", buildJsonObject {
          put("consent", true)
          put("product", buildJsonObject { put("name", name); put("barcode", barcode) })
        })
        return null
      }
    }
    return shownBytes(context, productId)
  }

  /* ------------------------- آماده‌سازی روی دستگاه ------------------------- */

  class Prepared(val display: ByteArray, val thumb: ByteArray?, val small: Boolean)

  /**
   *  چرخشِ EXIF با `Matrix` و آلفا روی زمینهٔ سفید (PNGِ شفاف در JPEG سیاه
   *  نشود) در یک کشیدن — بی آرایهٔ پیکسل. بومِ تازه، `src` دست‌نخورده.
   */
  fun upright(src: Bitmap, orientation: Int): Bitmap {
    val o = if (orientation in 2..8) orientation else 1
    val size = ImagePrep.oriented(src.width, src.height, o)
    val out = Bitmap.createBitmap(size.w, size.h, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
      drawColor(Color.WHITE)
      drawBitmap(src, Matrix().apply { setValues(ImagePrep.exifMatrix(o, src.width, src.height)) }, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
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
    val straight = try { upright(scaled, orientation) } finally { scaled.recycle() }
    val osz = ImagePrep.Size(straight.width, straight.height)
    try {
      fun encode(s: ImagePrep.Size, q: Int): ByteArray {
        val b = if (s.w == straight.width && s.h == straight.height) straight else Bitmap.createScaledBitmap(straight, s.w, s.h, true)
        val out = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q, out)
        if (b !== straight) b.recycle()
        return JpegMeta.strip(out.toByteArray())
      }
      val (display, dsz) = ImagePrep.encodeUnder(osz, ImagePrep.DISPLAY_BYTES, ::encode)
      val (thumb, tsz) = ImagePrep.encodeUnder(ImagePrep.fit(dsz.w, dsz.h, ImagePrep.THUMB_SIDE), ImagePrep.THUMB_BYTES, ::encode)
      Prepared(display, thumb.takeIf { ImagePrep.aspectOk(tsz, dsz) }, small = maxOf(w0, h0) < ImagePrep.MIN_SIDE)
    } finally {
      straight.recycle()
    }
  }

  fun sha256(b: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

  /**
   *  عکسِ خودِ دکان روی بانک: اصل ← نسخهٔ نمایش ← کوچک ← انتخاب به‌عنوانِ عکسِ
   *  این کالا. `up` پیشرفت است و پس از هر قدم با `onStep` ذخیره می‌شود.
   */
  suspend fun uploadOwn(context: Context, productId: String, prep: Prepared, up: BulkJob.Upload, onStep: () -> Unit = {}) {
    val api = Backend.api(context)
    if (up.imageId.isEmpty()) {
      //  POST خودکار تکرار نمی‌شود (`postBytes`)؛ کلید تا سرور هم تکرارِ پس از قطعی را «همان کار» بداند
      val r = api.postBytes(
        "$BASE?product_id=${enc(productId)}&role=main", prep.display, "image/jpeg",
        mapOf("Idempotency-Key" to sha256(prep.display)),
      )
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

  /**
   *  ⛔ هر ردیفِ صف شمارهٔ نسل (`g:<id>`) دارد: عکسِ تازه‌تر نسل را بالا می‌برد و
   *     `done`/پیشرفتِ عکسِ قدیمی‌تر (که هنوز در راه بود) فقط با همان نسل اثر
   *     دارد — عکسِ تازه با پایانِ قدیمی از صف نمی‌افتد و پیشرفتش را نمی‌گیرد.
   */
  private fun gen(p: android.content.SharedPreferences, id: String) = p.getLong("g:$id", 0L)

  /** عکسِ تازهٔ کالا (از استودیو) با بانکِ روشن ⇒ در صف برای بانکِ همین دکان */
  fun enqueueOwn(context: Context, productId: String) {
    if (enabled.value != true) return
    synchronized(this) {
      val p = prefs(context)
      val set = (p.getStringSet("own", emptySet()) ?: emptySet()).toMutableSet()
      set += productId
      p.edit().putStringSet("own", set).putLong("g:$productId", gen(p, productId) + 1).remove("up:$productId").apply()
    }
  }

  /** عکسِ کالا از راهِ دیگری آمد (بانکِ مرکزی، ورودِ گروهی) ⇒ عکسِ قدیمیِ در صف دیگر فرستاده نشود */
  private fun dropOwn(context: Context, productId: String) {
    synchronized(this) {
      val p = prefs(context)
      val set = (p.getStringSet("own", emptySet()) ?: emptySet()).toMutableSet()
      if (productId !in set && !p.contains("g:$productId")) return
      set -= productId
      p.edit().putStringSet("own", set).putLong("g:$productId", gen(p, productId) + 1).remove("up:$productId").apply()
    }
  }

  /**
   *  «عکسِ کالا روی دستگاه گذاشته شد» — یک جا برای همهٔ راه‌ها: همگام‌سازیِ امروز
   *  (`PhotoSync`) و اگر عکسِ خودِ دکان است (`own`) صفِ بانک و فرستادن در `scope`.
   */
  fun photoSaved(context: Context, productId: String, own: Boolean, scope: CoroutineScope? = null) {
    ir.vil3ntec.tohid.sync.PhotoSync.changed(context, productId)
    if (!own) { dropOwn(context, productId); return }
    if (enabled.value != true) return
    enqueueOwn(context, productId)
    scope?.launch { try { drain(context) } catch (c: CancellationException) { throw c } catch (_: Exception) {} }
  }

  suspend fun drain(context: Context) = lock.withLock {
    if (enabled.value != true) return@withLock
    val p = prefs(context)
    for (id in (p.getStringSet("own", emptySet()) ?: emptySet()).toList()) {
      val g = synchronized(this) { gen(p, id) }
      fun same() = gen(p, id) == g
      val done = {
        synchronized(this) {
          if (same()) {
            val set = (p.getStringSet("own", emptySet()) ?: emptySet()).toMutableSet()
            set -= id
            p.edit().putStringSet("own", set).remove("up:$id").remove("g:$id").apply()
          }
        }
      }
      val bytes = withContext(Dispatchers.IO) { PhotoStore.bytes(context, id) }
      if (bytes == null) { done(); continue }
      //  پیشرفت با نسلش ذخیره می‌شود؛ پیشرفتِ نسلِ دیگر یعنی از نو
      val saved = p.getString("up:$id", null)?.split('|')?.takeIf { it.getOrNull(0) == g.toString() }?.drop(1)
      val up = BulkJob.Upload(
        imageId = saved?.getOrNull(0).orEmpty(), version = saved?.getOrNull(1)?.toIntOrNull() ?: 1,
        display = saved?.getOrNull(2) == "1", thumb = saved?.getOrNull(3) == "1", selected = saved?.getOrNull(4) == "1",
      )
      try {
        val prep = prepare(bytes)
        uploadOwn(context, id, prep, up) {
          synchronized(this) {
            if (same()) p.edit().putString("up:$id", listOf(g, up.imageId, up.version, if (up.display) 1 else 0, if (up.thumb) 1 else 0, if (up.selected) 1 else 0).joinToString("|")).apply()
          }
        }
        done()
      } catch (c: CancellationException) {
        throw c
      } catch (f: Throwable) {
        if (transient(f)) return@withLock
        done()   //  عکس روی دستگاه و همگام‌سازیِ امروز سرِ جایش است؛ فقط بانک نپذیرفت
      }
    }
  }
}
