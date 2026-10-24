package ir.vil3ntec.tohid.core.atrest

import java.io.File
import java.security.SecureRandom

/**
 *  رمز و بازکردنِ بایت‌ها با کلیددانِ این دستگاه.
 *
 *  `open` هر دو قالب را می‌خواند: فایلِ ساده همان‌طور برمی‌گردد. پس هیچ
 *  خواننده‌ای به «روشن یا خاموش بودنِ رمز» وابسته نیست.
 */
class AtRestCodec(private val keyring: AtRestKeyring, private val random: SecureRandom = SecureRandom()) {

  /** @throws AtRestException اگر پاکت است و باز نمی‌شود */
  fun open(bytes: ByteArray): ByteArray =
    if (!AtRestEnvelope.isSealed(bytes)) bytes else AtRestEnvelope.open(bytes) { kid -> keyring.keyFor(kid) }

  /** @throws AtRestException `KEY_UNAVAILABLE` اگر کلیدی برای رمز کردن نیست */
  fun seal(plain: ByteArray): ByteArray {
    val (kid, key) = keyring.forWrite()
      ?: throw AtRestException(AtRestException.Kind.KEY_UNAVAILABLE, "کلیدِ رمز ساخته یا باز نشد")
    return AtRestEnvelope.seal(plain, kid, key, random)
  }
}

/**
 *  کوچِ یک فایل از ساده به رمزدار (و برعکس، برای برگشت) — یک بار، ایمن در
 *  برابرِ قطعِ برق.
 *
 *  ── گام‌ها ──────────────────────────────────────────────────────────
 *   ۰) فایلِ کناریِ مانده از تلاشِ نیمه‌کارهٔ قبلی (`<نام>.atrest-new`) پاک
 *      می‌شود — هرگز مرجع نیست، چون فایلِ اصلی همیشه کامل است.
 *   ۱) فایلِ اصلی خوانده می‌شود؛ اگر همین حالا به قالبِ خواسته است، کاری نیست.
 *   ۲) قالبِ تازه در فایلِ کناری نوشته و `fsync` می‌شود.
 *   ۳) فایلِ کناری **از روی دیسک** دوباره خوانده و باز می‌شود و با دادهٔ
 *      اصلی بایت‌به‌بایت مقایسه می‌شود. نخورد ⇒ کناری پاک، اصلی دست‌نخورده.
 *   ۴) جایگزینیِ اتمی: کناری جای اصلی می‌نشیند (نسخهٔ ساده همین‌جا از نامِ
 *      فایل برداشته می‌شود).
 *   ۵) `fsync`ِ پوشه.
 *
 *  هر لحظه که برق برود، فایلِ اصلی یا تمامِ قالبِ قبلی است یا تمامِ قالبِ
 *  تازه — و خواننده‌ها هر دو را می‌خوانند. اجرای دوباره همان‌جا را تمام می‌کند.
 *
 *  ⛔ صدازننده باید همان قفلی را نگه دارد که نویسندهٔ این فایل نگه می‌دارد؛
 *  وگرنه نوشتنِ تازه‌ای که میانِ گامِ ۱ و ۴ برسد زیرِ نسخهٔ کهنه می‌رود.
 *
 *  ⚠️ روی حافظهٔ فلش، بلوک‌های فایلِ سادهٔ قبلی ممکن است تا بازنویسیِ
 *  سیستم‌عامل فیزیکی بمانند؛ پاک‌کردنِ امن روی فلش ممکن نیست. رمز از این
 *  به بعد را تضمین می‌کند، نه پاک شدنِ گذشته.
 */
object AtRestMigration {

  enum class Step { SIDECAR_WRITTEN, VERIFIED, REPLACED }

  enum class Outcome { ABSENT, ALREADY, CONVERTED }

  const val SIDECAR_SUFFIX = ".atrest-new"

  fun sidecarOf(f: File): File = File(f.absoluteFile.parentFile, f.name + SIDECAR_SUFFIX)

  /**
   *  @param toSealed `true` = رمز کن؛ `false` = برگرداندن به ساده
   *  @param crash فقط برای آزمون: پس از هر گام صدا زده می‌شود و می‌تواند پرتاب کند
   *  @throws AtRestException اگر باز/رمز نشد یا بازبینی نخورد — فایلِ اصلی دست‌نخورده است
   */
  fun convert(
    f: File,
    toSealed: Boolean,
    codec: AtRestCodec,
    io: AtRestFiles,
    crash: (Step) -> Unit = {},
  ): Outcome {
    val side = sidecarOf(f)
    if (side.exists()) io.delete(side)
    if (!f.isFile) return Outcome.ABSENT
    //  اول فقط سرآیند (۸ بایت): در هر بازشدن کلِ دفتر و نسخه‌ها خوانده نمی‌شوند
    if (AtRestEnvelope.isSealed(f) == toSealed) return Outcome.ALREADY
    val mtime = f.lastModified()
    //  حافظه: دفترِ بزرگ روی گوشیِ کهنه — بایت‌های قبلی و تازه فقط درونِ همین
    //  بلوک زنده‌اند؛ بازبینی با اثرِ SHA-256ِ دادهٔ باز انجام می‌شود، نه نگه‌داشتنِ کپیِ سوم.
    val digest: ByteArray = run {
      val raw = io.read(f)
      val sealedNow = AtRestEnvelope.isSealed(raw)
      if (sealedNow == toSealed) return Outcome.ALREADY
      val plain = if (sealedNow) codec.open(raw) else raw
      val out = if (toSealed) codec.seal(plain) else plain
      try {
        io.writeSynced(side, out)
      } catch (e: java.io.IOException) {
        runCatching { io.delete(side) }
        throw AtRestException(AtRestException.Kind.IO, "«${f.name}» روی دیسک نوشته نشد: ${e.message}", e)
      }
      sha256(plain)
    }
    //  ⛔ زمانِ فایل همان می‌ماند: نگه‌داشتِ نسخه‌های خودکار («۶ آخر + ۷ روزانه
    //  + ۴ هفتگی») از همین زمان است و کوچ نباید همه را «امروزی» کند و هرس‌شان کند.
    //  پیش از جابه‌جایی، تا جابه‌جاییِ اتمی همین زمان را با خودش ببرد.
    if (mtime > 0) side.setLastModified(mtime)
    crash(Step.SIDECAR_WRITTEN)

    val ok = try {
      val back = io.read(side)
      AtRestEnvelope.isSealed(back) == toSealed && sha256(codec.open(back)).contentEquals(digest)
    } catch (e: AtRestException) {
      false
    } catch (e: java.io.IOException) {
      false
    }
    if (!ok) {
      runCatching { io.delete(side) }
      throw AtRestException(AtRestException.Kind.AUTH_FAILED, "بازبینیِ «${f.name}» پس از نوشتن نخورد؛ فایلِ اصلی دست نخورد")
    }
    crash(Step.VERIFIED)

    io.replace(side, f)
    crash(Step.REPLACED)

    f.absoluteFile.parentFile?.let { runCatching { io.syncDir(it) } }
    return Outcome.CONVERTED
  }

  private fun sha256(b: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(b)

  /** نتیجهٔ کوچِ چند فایل — برای پیامِ صفحه */
  data class Report(
    val converted: Int = 0,
    val already: Int = 0,
    val failed: List<Pair<String, AtRestException.Kind>> = emptyList(),
  ) {
    val ok: Boolean get() = failed.isEmpty()
  }

  /** کوچِ چند فایل؛ شکستِ یکی جلوی بقیه را نمی‌گیرد و هیچ فایلی را خراب نمی‌کند */
  fun convertAll(files: List<File>, toSealed: Boolean, codec: AtRestCodec, io: AtRestFiles): Report {
    var converted = 0
    var already = 0
    val failed = ArrayList<Pair<String, AtRestException.Kind>>()
    for (f in files) {
      try {
        when (convert(f, toSealed, codec, io)) {
          Outcome.CONVERTED -> converted++
          Outcome.ALREADY -> already++
          Outcome.ABSENT -> Unit
        }
      } catch (e: AtRestException) {
        failed += f.name to e.kind
      } catch (e: Exception) {
        failed += f.name to AtRestException.Kind.IO
      }
    }
    return Report(converted, already, failed)
  }
}
