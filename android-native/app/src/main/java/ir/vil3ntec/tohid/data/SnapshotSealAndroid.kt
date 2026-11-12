package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import ir.vil3ntec.tohid.core.atrest.platform.PhraseBackup
import ir.vil3ntec.tohid.core.backup.RecoveryPhrase
import ir.vil3ntec.tohid.core.i18n.StrBackupSafety
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 *  OPS-02 — رمزِ نسخه‌های خودکارِ دفتر با «عبارتِ بازیابیِ فروشگاه» (تنظیمِ **همین دستگاه**، پیش‌فرض **خاموش**).
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  نسخه‌های نیم‌ساعته (`auto-backups/`) رونوشتِ خامِ دفترند؛ با «رمزِ دفتر» ِ خاموش (پیش‌فرض) متنِ ساده‌اند و با
 *  پشتیبانِ خودکارِ اندروید و انتقالِ گوشی (`backup_rules.xml`: همهٔ `file`) بیرون می‌روند. با «رمزِ دفتر» ِ روشن
 *  با کلیدِ Keystore رمزند — یعنی روی دستگاهِ تازه هرگز باز نمی‌شوند. قاعدهٔ §۱۰.۳ (`docs/RULES-fa.md`): پشتیبانِ
 *  دستگاه با کلیدِ ساخته از عبارت رمز می‌شود، **هرگز** با کلیدِ دستگاه. پس:
 *
 *  ── چه می‌کند ────────────────────────────────────────────────────────
 *   • روشن ([on]) و کلید هست ⇒ هر نسخهٔ تازه همان پاکتِ `AtRestEnvelope` با KID ِ `rp1.<تکرار>.<نمک>`
 *     (`PhraseBackup.seal` — همان بردارهای Python ِ `PhraseBackupTest`؛ رمزِ تازه‌ای ساخته نشد). دستگاهِ تازه با
 *     خودِ عبارت بازش می‌کند (`PhraseBackup.open`).
 *   • کلیدِ ساخته‌شده (PBKDF2، یک بار هنگامِ تأییدِ عبارت) پیچیده در کلیددانِ همین دستگاه (`AtRest.codec`) در
 *     `atrest/phrase-keys` می‌ماند تا هر نیم ساعت عبارت پرسیده نشود؛ کلیدِ عبارت‌های پیشین هم تا وقتی نسخه‌ای
 *     با آن‌ها مانده.
 *   • فقط وقتی نسخهٔ تازه ساخته می‌شود که متنِ بازِ دفتر با متنِ بازِ تازه‌ترین نسخه فرق دارد (همان «فقط با تغییر»).
 *   • هر نسخهٔ رمزدار پس از نوشتن از روی دیسک باز و با دفتر سنجیده می‌شود؛ نخورد ⇒ پاک و همان رونوشتِ پیشین.
 *
 *  ── تضمینِ «گم شدنِ کلید تازه‌ترین داده را نمی‌برد» ───────────────────
 *   ۱) خودِ دفتر، `.prev`، `.tmp`، نسخهٔ ایمنیِ پیش از بازیابی، بایگانی‌ها، صف و پشتیبانِ ابری **هرگز** با این
 *      کلید رمز نمی‌شوند — تازه‌ترین داده هیچ‌وقت به آن بسته نیست. پشتیبانِ دستی/پوشه فقط با کلیدِ جدای `BackupSeal`
 *      (پیش‌فرض خاموش) و همان کلید؛ آن‌ها رونوشتِ بیرونیِ دفترند، نه جای آن.
 *   ۲) روشن شدن فقط پس از نشان دادن و **نوشتنِ دوبارهٔ** همان عبارت (`RecoveryPhraseFlow`)؛ هر نسخهٔ رمزدار
 *      نمک و تکرار را در سرآیندِ خودش دارد ⇒ فقط عبارت لازم است، روی هر دستگاهی.
 *   ۳) کلیدِ همین دستگاه باز نشد (Keystore پاک شد، پرونده خراب، گوشیِ تازه از پشتیبانِ اندروید) ⇒ نسخه‌ها مثلِ
 *      پیش (رونوشتِ خامِ همان دفترِ کنارش) گرفته می‌شوند — هرگز بی نسخه نمی‌مانیم، و نسخه هرگز کم‌محافظت‌تر از
 *      دفترِ کنارش نیست؛ صفحه «کلید باز نشد» می‌گوید. نسخه‌های رمزدارِ پیشین با عبارت باز می‌شوند.
 *   ۴) نسخه‌های سادهٔ پیشین (و رمزدار با کلیدِ دستگاه) همان‌طور خوانده می‌شوند؛ خاموش کردن هیچ نسخه‌ای را پاک یا
 *      بازنویسی نمی‌کند. کوچِ «رمزِ دفتر» (`AtRest.converge`) به نسخه‌های رمزدار با عبارت دست نمی‌زند.
 *
 *  آزمون: `BackupRestoreDrillTest` (کامپیوتر، همان کدِ گوشی).
 */
object SnapshotSeal {

  private const val PREFS = "tohid"

  /** کلیدِ تنظیم — مالِ **دستگاه** (کلیدِ پیچیده هم مالِ همین دستگاه است)، مثلِ `AtRest.PREF_KEY` */
  const val PREF_KEY = "snapshot_seal"

  /** پروندهٔ کلیدها کنارِ کلیددانِ دفتر */
  const val KEYS_FILE = "phrase-keys"

  private val lock = Any()
  private val random = SecureRandom()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(PREF_KEY, false) }.getOrDefault(false)

  /** روشن فقط وقتی کلیدِ عبارتی روی همین دستگاه هست ([adopt] پس از تأیید) */
  fun setOn(context: Context, on: Boolean): Boolean {
    if (on && current(context) == null) return false
    return prefs(context).edit().putBoolean(PREF_KEY, on).commit()
  }

  /** آخرین شکست (کلید باز نشد، نسخهٔ رمزدار سنجیده نشد) — برای صفحه */
  @Volatile var lastError: String? = null
    private set

  private fun keysFile(context: Context) = File(File(context.applicationContext.filesDir, "atrest"), KEYS_FILE)

  private class Entry(val kid: String, val raw: ByteArray)

  /** کلیدهای عبارت؛ پرونده نیست ⇒ خالی؛ باز یا خوانده نشد ⇒ `null` (کلید گم شده) */
  private fun entries(context: Context): List<Entry>? {
    val f = keysFile(context)
    if (!f.isFile) return emptyList()
    return runCatching {
      val bytes = f.readBytes()
      //  ⛔ فقط پروندهٔ پیچیده در کلیددان؛ پروندهٔ ساده (دست‌ساز) کلید نیست
      if (!AtRestEnvelope.isSealed(bytes)) error("not sealed")
      decode(String(AtRest.codec(context).open(bytes), Charsets.US_ASCII))
    }.getOrNull()
  }

  private fun decode(text: String): List<Entry> = text.lines().filter { it.isNotBlank() }.map { line ->
    val (kid, hex) = line.trim().split(' ').also { require(it.size == 2) }
    require(RecoveryPhrase.parseKid(kid) != null && hex.length == 64)
    Entry(kid, ByteArray(32) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() })
  }

  private fun encode(list: List<Entry>): ByteArray = list.joinToString("\n") { e ->
    e.kid + " " + e.raw.joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
  }.toByteArray(Charsets.US_ASCII)

  /** کلیدِ نوشتنِ نسخه‌ها (آخرین عبارتِ تأییدشده) */
  internal fun current(context: Context): Pair<String, SecretKey>? =
    entries(context)?.lastOrNull()?.let { it.kid to SecretKeySpec(it.raw, "AES") }

  /** همین دستگاه کلیدی برای نوشتن دارد — برای صفحه */
  fun keyReady(context: Context): Boolean = current(context) != null

  internal fun keyFor(context: Context, kid: String): SecretKey? =
    entries(context)?.lastOrNull { it.kid == kid }?.let { SecretKeySpec(it.raw, "AES") }

  /**
   *  عبارتِ تأییدشده (شکلِ استاندارد، `RecoveryPhraseFlow.takeConfirmed`) ⇒ کلیدِ تازه (PBKDF2، کُند — بیرون از
   *  رشتهٔ صفحه) کنارِ کلیدهای پیشینی که هنوز نسخه‌ای دارند. پروندهٔ کلیدِ پیشینی که دیگر باز نمی‌شود پاک نمی‌شود،
   *  کنار می‌رود (`.lost-<زمان>`). @return KID ِ تازه؛ کلیددان در دسترس نبود ⇒ پرتاب و هیچ چیزی عوض نمی‌شود.
   */
  fun adopt(context: Context, canonical: String, iterations: Int = RecoveryPhrase.ITERATIONS): String = synchronized(lock) {
    val (kid, key) = PhraseBackup.newKey(canonical, random, iterations)
    val f = keysFile(context)
    val old = entries(context)
    if (old == null && f.isFile) runCatching { f.renameTo(File(f.parentFile, "$KEYS_FILE.lost-${System.currentTimeMillis()}")) }
    val used = runCatching { AutoBackup.list(context).mapNotNull { kidOf(it.file) }.toSet() }.getOrDefault(emptySet())
    val keep = old.orEmpty().filter { it.kid != kid && it.kid in used }
    val sealed = AtRest.codec(context).seal(encode(keep + Entry(kid, key.encoded)))
    f.parentFile?.mkdirs()
    DiskWrite.bytes(f, sealed)
    if (keyFor(context, kid) == null) throw IOException(StrBackupSafety.keyFailed)
    lastError = null
    kid
  }

  /** KID ِ پاکتِ یک پرونده (فقط سرآیند)؛ ساده یا ناخوانا ⇒ `null` */
  fun kidOf(file: File): String? = runCatching {
    val head = file.inputStream().use { s ->
      val buf = ByteArray(512)
      var n = 0
      while (n < buf.size) { val r = s.read(buf, n, buf.size - n); if (r < 0) break; n += r }
      buf.copyOf(n)
    }
    if (!AtRestEnvelope.isSealed(head)) null else AtRestEnvelope.keyIdOf(head)
  }.getOrNull()

  /** با کلیدِ ساخته از عبارت رمز شده (نه کلیدِ دستگاه) */
  fun isPhraseSealed(file: File): Boolean = kidOf(file)?.let { RecoveryPhrase.parseKid(it) != null } == true

  /**
   *  بایت‌های بازِ یک نسخهٔ خودکار (یا هر پروندهٔ دفتر): رمزدار با عبارت ⇒ کلیدِ همین دستگاه، یا [typed] (عبارتی
   *  که کاربر نوشت)؛ بقیه ⇒ همان `AtRest` (ساده یا کلیدِ دستگاه).
   *  @throws AtRestException `KEY_MISSING` ⇒ کلیدش این‌جا نیست و [typed] داده نشد (یا عبارت نادرست است)؛
   *    `AUTH_FAILED` ⇒ عبارتِ دیگر یا پروندهٔ دست‌خورده.
   */
  fun readBytes(context: Context, file: File, typed: String? = null): ByteArray {
    val bytes = file.readBytes()
    val kid = if (AtRestEnvelope.isSealed(bytes)) AtRestEnvelope.keyIdOf(bytes) else null
    if (kid == null || RecoveryPhrase.parseKid(kid) == null) return AtRest.codec(context).open(bytes)
    if (typed != null) return PhraseBackup.open(bytes, typed)
    return AtRestEnvelope.open(bytes) { k -> keyFor(context, k) }
  }

  fun readText(context: Context, file: File): String = String(readBytes(context, file), Charsets.UTF_8)

  /** نتیجهٔ [take] */
  sealed interface Took {
    /** نسخهٔ رمزدارِ تازه */
    class Made(val file: File) : Took
    /** کاری نبود: دفتر عوض نشده، نیست، یا خوانده/سنجیده نشد (دفترِ خراب نسخه نمی‌شود) */
    data object Unchanged : Took
    /** خاموش، یا کلید/رمز نشد ⇒ همان رونوشتِ پیشین (`LocalSnapshots.takeIfChanged`) */
    data object NotSealed : Took
  }

  /**
   *  نسخهٔ رمزدار، اگر روشن است و کلید هست — `AutoBackup.take`.
   *  @param valid دفترِ باز (متن) درست خوانده می‌شود — همان `LedgerRecovery.decode`
   */
  fun take(context: Context, ledger: File, dir: File, name: String, valid: (ByteArray) -> Boolean): Took {
    if (!on(context)) return Took.NotSealed
    val (kid, key) = current(context) ?: run { lastError = StrBackupSafety.sealKeyMissing; return Took.NotSealed }
    if (!ledger.isFile || ledger.length() == 0L) return Took.Unchanged
    //  همان خواندنِ بی‌قفلِ رونوشت (ثبتِ فروش روی ویندوز منتظر نمی‌ماند)؛ دفترِ رمزدار با کلیدِ دستگاه باز می‌شود
    val plain = runCatching { AtRest.codec(context).open(LocalSnapshots.openShared(ledger).use { it.readBytes() }) }.getOrNull()
      ?: return Took.Unchanged
    if (!valid(plain)) return Took.Unchanged
    return try {
      val made = LocalSnapshots.takeSealedIfChanged(
        plain, dir, name,
        seal = { PhraseBackup.seal(it, kid, key, random) },
        open = { readBytes(context, it) },
      )
      lastError = null
      if (made == null) Took.Unchanged else Took.Made(made)
    } catch (e: Exception) {
      lastError = StrBackupSafety.sealKeyMissing
      Took.NotSealed
    }
  }
}
