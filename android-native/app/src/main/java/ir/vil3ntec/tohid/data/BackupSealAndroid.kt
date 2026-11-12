package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.platform.PhraseBackup
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

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
 *   • بازیابیِ رمزدار کلِ فایل را در حافظه باز می‌کند (برچسبِ GCM فقط در پایان سنجیده می‌شود).
 *
 *  آزمون: `BackupSealDrillTest` (کامپیوتر، همان کدِ گوشی).
 */
object BackupSeal {

  private const val PREFS = "tohid"

  /** کلیدِ تنظیم — مالِ **دستگاه** (کلیدِ عبارت هم مالِ همین دستگاه است)، مثلِ `SnapshotSeal.PREF_KEY` */
  const val PREF_KEY = "backup_seal"

  /** پسوندِ پشتیبانِ رمزدار — ZIP نیست، تا برنامهٔ فشرده‌سازِ دیگر «فایلِ خراب» نگوید */
  const val SEALED_EXT = ".zip.sealed"

  private val random = SecureRandom()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(PREF_KEY, false) }.getOrDefault(false)

  /** روشن فقط وقتی کلیدِ عبارتی روی همین دستگاه هست (`SnapshotSeal.adopt` پس از تأیید) */
  fun setOn(context: Context, on: Boolean): Boolean {
    if (on && SnapshotSeal.current(context) == null) return false
    return prefs(context).edit().putBoolean(PREF_KEY, on).commit()
  }

  /**
   *  رمزگذارِ پشتیبانِ بعدی — **پیش از** باز کردنِ مقصد صدا زده شود: خاموش ⇒ `null` (ZIP ِ ساده)؛ روشن ⇒ پیچنده؛
   *  روشن و کلید باز نشد ⇒ پرتاب با پیامِ روشن (هیچ فایلی ساخته نمی‌شود).
   */
  fun sealer(context: Context): ((OutputStream) -> OutputStream)? {
    if (!on(context)) return null
    val (kid, key) = SnapshotSeal.current(context) ?: throw IllegalStateException(StrBackupSafety.backupSealKeyMissing)
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
    val bytes = head.readBytes()
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

  /** فایلِ رمزدار با عبارت که کلیدش این‌جا نیست ⇒ صفحه عبارت را می‌پرسد */
  fun needsPhrase(e: Throwable?): Boolean = e is AtRestException && e.kind == AtRestException.Kind.KEY_MISSING
}
