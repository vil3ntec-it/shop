package ir.vil3ntec.tohid.data

import android.content.Context
import android.content.SharedPreferences
import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.platform.PhraseBackup
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 *  OPS-02 — رمزِ **پشتیبانِ دستی و پوشه** (ZIP ِ کامل: دفتر، تنظیمات، نگه‌داشته‌ها و عکس‌ها) با «عبارتِ بازیابیِ فروشگاه»
 *  (§۱۰.۳)، درونِ برنامه. تنظیمِ **همین دستگاه**، پیش‌فرض **خاموش**؛ خاموش ⇒ ZIP بایت‌به‌بایت همان پیشین.
 *
 *  ── قالب ─────────────────────────────────────────────────────────────
 *  کلِ ZIP درونِ همان پاکتِ `AtRestEnvelope` با KID ِ `rp1.<تکرار>.<نمک>` (`PhraseBackup.sealing`، جریانی) — همان قراردادِ
 *  `server/scripts/phrase-backup.js` (`seal`/`open`): ZIP ِ رمزشده با ابزارِ بیرونی در برنامه باز می‌شود و برعکس. رمزِ تازه‌ای
 *  ساخته نشد (javax.crypto: AES-256-GCM، PBKDF2-HMAC-SHA256).
 *
 *  ── کلید ─────────────────────────────────────────────────────────────
 *  همان کلیدِ ساخته از عبارت که «رمزِ نسخه‌های خودکار» نگه می‌دارد (`SnapshotSeal.current`، پیچیده در کلیددانِ دستگاه)؛
 *  پس روشن شدن فقط پس از نشان دادن و نوشتنِ دوبارهٔ عبارت. نمک و تکرار در سرآیندِ هر فایل است ⇒ دستگاهِ تازه فقط عبارت
 *  لازم دارد.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *   • روشن و کلید باز نشد ⇒ پشتیبان **ساخته نمی‌شود** (پیامِ روشن) — هرگز بی‌صدا ساده در پوشه/USB؛ دفتر و نسخه‌های
 *     خودکارِ درونِ برنامه و پشتیبانِ سرور مثلِ پیش می‌مانند، پس تازه‌ترین داده هرگز به این کلید بسته نیست.
 *   • خودِ دفتر، `.prev`، نسخهٔ ایمنی، بایگانی، صف و پشتیبانِ سرور با این کلید رمز نمی‌شوند.
 *   • بازیابی ([unwrap]): ساده ⇒ همان راهِ پیشین؛ رمزدار ⇒ کلیدِ همین دستگاه، وگرنه عبارتی که کاربر می‌نویسد. عبارتِ نادرست،
 *     پاکتِ دست‌خورده/بریده یا رمزشده با کلیدِ دستگاه ⇒ رد پیش از هر نوشتنی (برچسبِ GCM؛ هیچ بایتِ باز پیش از سنجش نیست).
 *   • بازیابیِ رمزدار کلِ فایل را در حافظه باز می‌کند (برچسبِ GCM فقط در پایان سنجیده می‌شود)، و روی اندروید رمزِ «جریانی» هم
 *     کلِ ZIP را تا پایان در حافظه نگه می‌دارد (Conscrypt ورودیِ AES-GCM را در بافرِ دوبرابرشونده جمع می‌کند): نوشتن ≈ ۳×،
 *     بازیابی ≈ ۴× اندازه. پس پیش از هر دو با حافظهٔ آزادِ همین فرآیند سنجیده می‌شود ([blocked]، [unwrap]) و پیامِ روشن
 *     می‌آید، نه OOM ِ بی‌صدا. مانده (STATUS ِ OPS-02): پاکتِ تکه‌ایِ نسخهٔ ۲ (STREAM) تا اندازه دیگر سقف نباشد.
 *   • پرچم در پروندهٔ همین دستگاه ([BackupDevice.PREFS]) است، نه در `tohid` که با پشتیبانِ اندروید به گوشیِ تازه می‌رفت.
 *
 *  آزمون: `BackupSealDrillTest` (کامپیوتر، همان کدِ گوشی).
 */
object BackupSeal {

  /** پروندهٔ پیشینِ پرچم — با پشتیبانِ اندروید جابه‌جا می‌شد ([migrate]) */
  private const val OLD_PREFS = "tohid"

  /** کلیدِ تنظیم — مالِ **دستگاه** (کلیدِ عبارت هم مالِ همین دستگاه است)، در [BackupDevice.PREFS] */
  const val PREF_KEY = "backup_seal"

  /** پسوندِ پشتیبانِ رمزدار — ZIP نیست، تا برنامهٔ فشرده‌سازِ دیگر «فایلِ خراب» نگوید */
  const val SEALED_EXT = ".zip.sealed"

  /** پسوندِ «پشتیبانِ فقط دفتر» ِ رمزدار (`….json.sealed`) */
  const val SEALED_SUFFIX = ".sealed"

  /** رمزِ اندروید ورودی را تا پایان نگه می‌دارد و بافرش دوبرابر می‌شود: نوشتن ≈ ۳×، بازیابی ≈ ۴× اندازهٔ پشتیبان */
  private const val SEAL_FACTOR = 3L
  private const val OPEN_FACTOR = 4L
  private const val MB = 1L shl 20

  /** تنظیمات، پیش‌فاکتورها و نگه‌داشته‌ها در ZIP — کنارِ دفتر و عکس‌ها */
  private const val MARGIN = MB

  /** حافظهٔ آزادِ همین فرآیند (بایت) — آزمون جایش را می‌گیرد */
  internal var heapFree: () -> Long = {
    val r = Runtime.getRuntime()
    r.maxMemory() - (r.totalMemory() - r.freeMemory())
  }

  private val random = SecureRandom()

  private fun prefs(context: Context): SharedPreferences = BackupDevice.prefs(context).also { migrate(context, it) }

  /**
   *  پرچمِ ساختِ پیشین (در `tohid`) یک بار به پروندهٔ همین دستگاه می‌آید و از `tohid` پاک می‌شود. مقدار همان می‌ماند، حتی
   *  بی کلید: خاموش کردنِ بی‌صدا پشتیبانِ بعدی را بی‌رمز بیرون می‌فرستاد؛ روشنِ بی کلید فقط پیامِ روشن می‌دهد ([blocked]).
   */
  private fun migrate(context: Context, to: SharedPreferences) {
    val old = context.applicationContext.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
    if (!old.contains(PREF_KEY)) return
    if (!to.contains(PREF_KEY)) to.edit().putBoolean(PREF_KEY, old.getBoolean(PREF_KEY, false)).commit()
    old.edit().remove(PREF_KEY).commit()
  }

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(PREF_KEY, false) }.getOrDefault(false)

  /** روشن فقط وقتی کلیدِ عبارتی روی همین دستگاه هست (`SnapshotSeal.adopt` پس از تأیید) */
  fun setOn(context: Context, on: Boolean): Boolean {
    if (on && SnapshotSeal.current(context) == null) return false
    return prefs(context).edit().putBoolean(PREF_KEY, on).commit()
  }

  /** برآوردِ اندازهٔ پشتیبان: دفترِ روی دیسک، عکس‌ها ([photos] ⇒ پشتیبانِ کامل) و حاشیه */
  fun estimate(context: Context, photos: Boolean): Long {
    val ledger = File(context.filesDir, "shop-data.json").length()
    val pics = if (!photos) 0L else BackupBundle.photoDir(context).listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
    return ledger + pics + MARGIN
  }

  /**
   *  چرا پشتیبانِ رمزدارِ بعدی ساخته نمی‌شود — پیامِ روشن، یا `null` (می‌شود، یا رمز خاموش است). **پیش از** باز کردنِ انتخابگر
   *  و مقصد: کلیدِ عبارت باز نشد، یا پشتیبان برای حافظهٔ آزادِ این دستگاه بزرگ است.
   */
  fun blocked(context: Context, photos: Boolean = true): String? =
    if (!on(context)) null else runCatching { keyFor(context, photos) }.exceptionOrNull()?.let { it.message ?: StrBackupSafety.backupSealKeyMissing }

  private fun keyFor(context: Context, photos: Boolean): Pair<String, SecretKey> {
    val current = SnapshotSeal.current(context) ?: throw IllegalStateException(StrBackupSafety.backupSealKeyMissing)
    val need = estimate(context, photos)
    val free = heapFree()
    if (need > free / SEAL_FACTOR) throw IllegalStateException(StrBackupSafety.backupTooBigToSeal(need / MB + 1, maxOf(0L, free / MB)))
    return current
  }

  /**
   *  رمزگذارِ پشتیبانِ بعدی — **پیش از** باز کردنِ مقصد صدا زده شود: خاموش ⇒ `null` (ZIP ِ ساده)؛ روشن ⇒ پیچنده؛
   *  روشن و کلید باز نشد یا پشتیبان برای حافظه بزرگ است ⇒ پرتاب با پیامِ روشن (هیچ فایلی ساخته نمی‌شود، هرگز ساده).
   *  @param photos پشتیبانِ کامل (با عکس‌ها)؛ `false` ⇒ «فقط دفتر»
   */
  fun sealer(context: Context, photos: Boolean = true): ((OutputStream) -> OutputStream)? {
    if (!on(context)) return null
    val (kid, key) = keyFor(context, photos)
    return { out -> PhraseBackup.sealing(out, kid, key, random) }
  }

  /**
   *  ورودیِ «بازیابی از فایل» ⇒ جریانِ ZIP/JSON ِ باز.
   *  @param typed عبارتی که کاربر برای فایلِ رمزدارِ بی‌کلید نوشت
   *  @throws AtRestException `KEY_MISSING` ([needsPhrase]) — رمزدار و کلیدش این‌جا نیست، عبارت بپرس
   *  @throws IllegalStateException با پیامِ روشن — عبارتِ نادرست، یا فایلِ دست‌خورده/ناقص/بیگانه
   */
  fun unwrap(context: Context, input: InputStream, typed: String? = null): InputStream {
    val head = input.buffered(DEFAULT_BUFFER_SIZE)
    head.mark(AtRestEnvelope.MAGIC.size)
    val magic = ByteArray(AtRestEnvelope.MAGIC.size)
    var n = 0
    while (n < magic.size) { val r = head.read(magic, n, magic.size - n); if (r < 0) break; n += r }
    head.reset()
    if (n < magic.size || !AtRestEnvelope.isSealed(magic)) return head
    //  بازیابیِ رمزدار ≈ ۴× اندازه در حافظه ⇒ بیش از سهمِ حافظهٔ آزاد خوانده نمی‌شود (پیامِ روشن، نه OOM)
    val limit = heapFree() / OPEN_FACTOR
    val bytes = readAtMost(head, limit) ?: error(StrBackupSafety.backupTooBigToOpen(maxOf(0L, limit / MB)))
    //  پاکتِ کلیدِ دستگاه (نه عبارت) یا سرآیندِ خراب: هیچ عبارتی بازش نمی‌کند
    val kid = AtRestEnvelope.keyIdOf(bytes)
    if (kid == null || RecoveryPhrase.parseKid(kid) == null) error(StrBackupSafety.damaged)
    val plain = try {
      if (typed != null) PhraseBackup.open(bytes, typed) else AtRestEnvelope.open(bytes) { k -> SnapshotSeal.keyFor(context, k) }
    } catch (e: AtRestException) {
      when {
        typed != null -> throw IllegalStateException(StrBackupSafety.wrongPhraseFile, e)
        e.kind == AtRestException.Kind.KEY_MISSING -> throw AtRestException(AtRestException.Kind.KEY_MISSING, StrBackupSafety.fileNeedsPhrase, e)
        else -> throw IllegalStateException(StrBackupSafety.damaged, e)
      }
    }
    return ByteArrayInputStream(plain)
  }

  /** همهٔ جریان، یا `null` اگر از [limit] بایت بیشتر است */
  private fun readAtMost(input: InputStream, limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
      val r = input.read(buf, 0, buf.size)
      if (r < 0) return out.toByteArray()
      total += r
      if (total > limit) return null
      out.write(buf, 0, r)
    }
  }

  /** فایلِ رمزدار با عبارت که کلیدش این‌جا نیست ⇒ صفحه عبارت را می‌پرسد */
  fun needsPhrase(e: Throwable?): Boolean = e is AtRestException && e.kind == AtRestException.Kind.KEY_MISSING
}
