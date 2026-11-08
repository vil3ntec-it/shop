package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.IOException

/**
 *  جای سایه و cursor ِ Sync v1 برای یک کلیدِ حساب — PERF-LEDGER، پشتِ پرچمِ دکانِ `shop.sync_shadow_file`
 *  (**پیش‌فرض خاموش**، `ShadowFileFlag`). شرحِ کامل: `docs/PERF-LEDGER-fa.md` §۱۰ و `docs/DATA-SAFETY-fa.md`.
 *
 *  ── چرا ──────────────────────────────────────────────────────────────
 *  سایه (کلِ دفتر) در `tohid-sync-v1` ِ SharedPreferences بود: هر `apply` ِ هر کلیدِ همان پرونده (cursor،
 *  `lastPushAt`، `lastError`…) کلِ XML را با سایه از نو می‌نوشت، اندروید در `onPause`/`onStop` رشتهٔ صفحه را برای
 *  نوشتن‌های مانده نگه می‌داشت و نخستین `getString` تا تجزیهٔ کلِ XML صبر می‌کرد؛ آیفون (NSUserDefaults) سقفِ عملیِ
 *  ~۴ مگ دارد و `commit()` ِ shim همیشه `true` است.
 *
 *  ── روشن ─────────────────────────────────────────────────────────────
 *  سایه و cursor در **یک فایل و یک نوشتن** (`ShadowFile`، همان `DiskWrite` با `.prev`). نامِ فایل در نشانِ prefs
 *  (`<کلید>.shadowStore`) است، پس جابه‌جاییِ حالِ حساب (`SyncV1Keys.move`) همان یک `commit` ِ prefs می‌ماند.
 *   • جا: `filesDir/sync-shadow/` ([DIR]) — بیرون از پشتیبانِ خودکارِ اندروید (`backup_rules*.xml`)، مثلِ `tohid-sync-v1`
 *     که سایه پیش‌تر در آن بود: نشانِ فایل با پشتیبان نمی‌رود، پس گوشیِ بازیابی‌شده (`RestoredDevice`) آن را هرگز
 *     نمی‌خواند، و فایلی هم‌اندازهٔ دفتر سقفِ ۲۵ مگیِ پشتیبانِ ابری را پر می‌کرد (پشتیبانِ کلِ برنامه می‌افتاد). فایلِ
 *     ساختِ پیشین در خودِ `filesDir` در نخستین خواندن به پوشه می‌رود ([located]).
 *   • نشانِ `copy` (پس از کوچ): نسخهٔ prefs هنوز هست و همان فایلِ همان نشان است؛ نخستین نوشتنِ موفقِ فایل آن را
 *     برمی‌دارد (نشانِ `file`) و XML کوچک می‌شود.
 *   • cursor ِ prefs فقط **آینه** است (نوار و برنامهٔ کهنه‌تر)، هرگز منبعِ این ساخت.
 *  ── خاموش ────────────────────────────────────────────────────────────
 *  بیت‌به‌بیت همان prefs ِ پیشین. فایلی که مانده بود یک بار (در شروع) در prefs می‌نشیند و پاک می‌شود.
 *
 *  ⛔ هیچ خواندنِ ناموفقی «سایهٔ خالی» نیست (#989): اصل ⇐ `.prev` ⇐ نسخهٔ prefs؛ هر برگشت به نسخهٔ کهنه‌تر
 *  `recoverArchive` را می‌گذارد (نه تفاضل، نه push، نه pull تا هم‌ترازیِ Snapshot ِ `recoverArchived`) — سایهٔ کهنه
 *  کنارِ صفِ تازه یعنی opِ تازه برای همان تغییر و `$inc` ِ دوبار. هیچ‌کدام نیست ⇒ پرتاب.
 *  پرچم فقط در شروع (نخستین دسترسی پس از ساخت/`invalidate`) خوانده می‌شود.
 */
internal class ShadowStore private constructor(context: Context, private val key: String) {

  private val app = context.applicationContext
  private val prefs: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val dir: File by lazy { dirOf(app) }
  private fun k(name: String) = "$key.$name"

  private enum class Mode { PREFS, FILE }

  private var mode: Mode? = null
  private var failure: IOException? = null

  /** FILE: فایلِ نشان؛ `null` ⇒ هنوز ساخته نشده (حالِ خالی، نخستین نوشتن می‌سازد) */
  private var file: ShadowFile? = null
  private var shadow = ""
  private var cursor = 0L
  private var gen = 0L
  private var copyPhase = false

  /** cursor ِ FILE ِ بارشده برای نوار، بی قفل؛ `null` ⇒ prefs */
  @Volatile private var seen: Long? = null
  @Volatile private var hasShadow = false

  // ─────────────── آنچه `SyncStateV1` می‌خواند ───────────────

  @Synchronized fun shadow(): String {
    ensure()
    return if (mode == Mode.FILE) shadow else prefs.getString(k("shadow"), "").orEmpty()
  }

  @Synchronized fun cursor(): Long {
    ensure()
    return if (mode == Mode.FILE) cursor else prefs.getLong(k("cursor"), 0)
  }

  /** فقط نمایش: هرگز فایل را نمی‌خواند و قفل نمی‌گیرد (رشتهٔ صفحه، `SyncV1Engine.snapshotStatus`) */
  fun cursorSeen(): Long = seen ?: prefs.getLong(k("cursor"), 0)

  /** ردِ همگام‌سازی، بی خواندنِ فایل (`SyncV1Keys.hasState`) */
  fun present(): Boolean {
    seen?.let { return it > 0 || hasShadow }
    return prefs.getLong(k("cursor"), 0) > 0 || !prefs.getString(k("shadow"), "").isNullOrBlank() || prefs.contains(k(MARKER))
  }

  @Synchronized fun setCursor(v: Long) {
    ensure()
    if (mode != Mode.FILE) { prefs.edit().putLong(k("cursor"), v).apply(); return }
    if (v == cursor) return
    if (!writeFile(shadow, v)) throw IOException(NOT_WRITTEN)
  }

  @Synchronized fun setShadow(v: String) {
    ensure()
    if (mode != Mode.FILE) { prefs.edit().putString(k("shadow"), v).apply(); return }
    if (!writeFile(v, cursor)) throw IOException(NOT_WRITTEN)
  }

  /** سایه و cursor با هم و همزمان — F0-05. `cursor == null` ⇒ دست نخورد */
  @Synchronized fun commit(shadow: String, cursor: Long?): Boolean {
    ensure()
    if (mode != Mode.FILE) {
      val e = prefs.edit().putString(k("shadow"), shadow)
      if (cursor != null) e.putLong(k("cursor"), cursor)
      return e.commit()
    }
    return writeFile(shadow, cursor ?: this.cursor)
  }

  /**
   *  پیش از سنجیدنِ `recoverArchive` (`SyncStateV1`): برگشت به نسخهٔ کهنه‌تر همین‌جا دیده و نشان گذاشته شود، پیش از
   *  هر تفاضل. روی رشتهٔ صفحه هیچ (خواندنِ فایل نه) — کارِ پس‌زمینهٔ بعدی همین را می‌کند.
   */
  fun settleOffMain() {
    if (onMainThread()) return
    synchronized(this) { runCatching { ensure() } }
  }

  /** `SyncStateV1.reset`: فایل و نشان هم */
  @Synchronized fun reset() {
    val named = prefs.getString(k(MARKER), null)?.let { Marker.parse(it) }
    if (named != null) prefs.edit().remove(k(MARKER)).commit()
    named?.let { ShadowFile(File(dir, it.name)).delete(); ShadowFile(File(app.filesDir, it.name)).delete() }
    file?.delete()
    clear()
  }

  /** حالِ این کلید روی دیسک عوض شد (`SyncV1Keys.move`) — دسترسیِ بعدی از نو */
  @Synchronized fun invalidate() = clear()

  private fun clear() {
    mode = null; failure = null; file = null; shadow = ""; cursor = 0; gen = 0; copyPhase = false
    seen = null; hasShadow = false
  }

  // ─────────────── نوشتن ───────────────

  /** FILE: یک نوشتن (سایه + cursor)، بعد prefs: برداشتنِ نسخهٔ کوچ‌کرده و آینهٔ cursor */
  private fun writeFile(text: String, c: Long): Boolean {
    var f = file
    var first = false
    if (f == null) {
      gen = nextGen()
      f = ShadowFile(File(dir, nameFor(gen)))
      first = true
    }
    val g = gen + 1
    try {
      f.write(text, c, g)
    } catch (e: IOException) {
      android.util.Log.w(TAG, "shadow file not written: ${e.message}")
      if (first) f.delete()
      return false
    }
    crash?.invoke(Step.COMMIT_WRITTEN)
    val mirror = prefs.getLong(k("cursor"), 0)
    if (first || copyPhase || mirror != c || prefs.contains(k("shadow"))) {
      val e = prefs.edit().putLong(k("cursor"), c).remove(k("shadow"))
      if (first || copyPhase) e.putString(k(MARKER), Marker(false, f.file.name, g).text())
      val ok = e.commit()
      //  نخستین فایل بی نشان پیدا نمی‌شود ⇒ نوشته نشده است. نسخهٔ کوچ‌کرده که برداشته نشد ⇒ فایل همچنان منبع است
      //  (نشانِ `copy` با شمارهٔ کهنه‌تر: بارِ بعد همین را تمام می‌کند)
      if (!ok && first) { f.delete(); return false }
      if (ok) copyPhase = false
    }
    file = f; shadow = text; cursor = c; gen = g
    seen = c; hasShadow = text.isNotBlank()
    return true
  }

  // ─────────────── شروع: کدام منبع ───────────────

  private fun ensure() {
    if (mode != null) return
    failure?.let { throw it }
    val on = ShadowFileFlag.enabled(app)
    val raw = prefs.getString(k(MARKER), null)
    if (raw == null) {
      if (on) migrateFromPrefs(stale = null) else mode = Mode.PREFS
      return
    }
    //  نشانِ ناشناخته مالِ ساختِ تازه‌تری است — هرگز حدس نه
    val m = Marker.parse(raw) ?: fail(ShadowFile.Corrupt("marker", raw.take(80)))
    noteMainThread()
    val f = located(m.name)
    val prefsShadow = prefs.getString(k("shadow"), null)
    //  پس از برداشتنِ نسخهٔ prefs، سایهٔ prefs فقط از ساختِ کهنه‌تری آمده که پس از ما نوشت (برگشتِ نسخه) ⇒ آن منبع است
    if (!m.copy && prefsShadow != null) return prefsWin(f, on)
    val loaded = try {
      f.load()
    } catch (e: IOException) {
      if (m.copy && prefsShadow != null) {
        //  فایل و `.prev` هیچ‌کدام سالم نیستند؛ نسخهٔ prefs ِ کوچ هنوز هست — شاید کهنه‌تر از فایلِ خراب ⇒ نشان
        gate(f)
        return prefsWin(f, on)
      }
      fail(e)
    }
    if (loaded == null) {
      if (m.copy) { gate(f); return prefsWin(f, on) }
      fail(ShadowFile.Corrupt("missing", m.name))
    }
    val p = loaded.pair
    //  نشانِ `copy` و همان نوشتنِ کوچ: نسخهٔ prefs باید همان باشد؛ نیست ⇒ ساختِ کهنه‌تری پس از کوچ نوشت
    if (m.copy && !loaded.fromPrevious && p.gen == m.gen &&
      ((prefsShadow ?: "") != p.shadow || prefs.getLong(k("cursor"), 0) != p.cursor)
    ) return prefsWin(f, on)
    if (loaded.fromPrevious) gate(f)
    if (!on) return backToPrefs(f, p)
    mode = Mode.FILE; file = f; shadow = p.shadow; cursor = p.cursor; gen = p.gen; copyPhase = m.copy
    seen = p.cursor; hasShadow = p.shadow.isNotBlank()
  }

  /** prefs منبع است: روشن ⇒ کوچِ دوباره به فایلِ تازه؛ خاموش ⇒ فقط نشان برداشته و فایل پاک */
  private fun prefsWin(stale: ShadowFile, on: Boolean) {
    if (on) return migrateFromPrefs(stale)
    if (!prefs.edit().remove(k(MARKER)).commit()) fail(IOException(NOT_WRITTEN))
    stale.delete()
    mode = Mode.PREFS
  }

  /**
   *  کوچ: prefs ⇐ فایل ⇐ دوباره‌خوانی و سنجش ⇐ نشانِ `copy` (همزمان). هر شکست ⇒ همان prefs برای همین اجرا (منبع هنوز
   *  همان است). بسته شدن در هر گام ⇒ پیش از نشان هیچ چیزی عوض نشده (کوچِ دوباره)، پس از آن فایل منبع است.
   */
  private fun migrateFromPrefs(stale: ShadowFile?) {
    val s = prefs.getString(k("shadow"), null).orEmpty()
    val c = prefs.getLong(k("cursor"), 0)
    if (s.isEmpty() && c == 0L) {
      //  چیزی برای کوچ نیست: نخستین نوشتن فایل و نشان را می‌سازد
      if (stale != null) { if (!prefs.edit().remove(k(MARKER)).commit()) fail(IOException(NOT_WRITTEN)); stale.delete() }
      mode = Mode.FILE; file = null; shadow = ""; cursor = 0; gen = 0; copyPhase = false; seen = 0; hasShadow = false
      return
    }
    noteMainThread()
    val g = nextGen()
    val f = ShadowFile(File(dir, nameFor(g)))
    val ok = runCatching {
      val sha = f.write(s, c, g)
      crash?.invoke(Step.MIGRATE_WRITTEN)
      val back = f.readMain()
      back != null && back.sha == sha && back.cursor == c && back.gen == g
    }.getOrElse { if (it is CrashForTest) throw it; false }
    if (!ok) { f.delete(); mode = Mode.PREFS; return }
    crash?.invoke(Step.MIGRATE_VERIFIED)
    if (!prefs.edit().putString(k(MARKER), Marker(true, f.file.name, g).text()).commit()) { f.delete(); mode = Mode.PREFS; return }
    crash?.invoke(Step.MIGRATED)
    if (stale != null && stale.file.name != f.file.name) stale.delete()
    sweep(g)
    mode = Mode.FILE; file = f; shadow = s; cursor = c; gen = g; copyPhase = true
    seen = c; hasShadow = s.isNotBlank()
  }

  /** پرچمِ خاموش: جفتِ فایل در prefs (یک `commit`، با برداشتنِ نشان) ⇐ پاک کردنِ فایل */
  private fun backToPrefs(f: ShadowFile, p: ShadowFile.Pair) {
    noteMainThread()
    val ok = prefs.edit().putString(k("shadow"), p.shadow).putLong(k("cursor"), p.cursor).remove(k(MARKER)).commit()
    if (!ok) {
      //  نوشته نشد ⇒ همین اجرا هنوز با فایل (منبع همان است)
      mode = Mode.FILE; file = f; shadow = p.shadow; cursor = p.cursor; gen = p.gen; copyPhase = false
      seen = p.cursor; hasShadow = p.shadow.isNotBlank()
      return
    }
    crash?.invoke(Step.BACK_WRITTEN)
    f.delete()
    mode = Mode.PREFS
  }

  /**
   *  برگشت به نسخهٔ کهنه‌تر (`.prev` یا prefs ِ کوچ): `recoverArchive` ⇐ هم‌ترازی با Snapshot پیش از هر تفاضل؛ فایلِ
   *  خرابِ اصل کنار می‌رود (هرگز پاک نه) تا نوشتنِ بعدی `.prev` ِ سالم را با آن عوض نکند.
   */
  private fun gate(f: ShadowFile) {
    prefs.edit().putBoolean(k("recoverArchive"), true).commit()
    if (f.file.isFile) runCatching { f.file.renameTo(File(f.file.absoluteFile.parentFile, f.file.name + ".corrupt-" + clock())) }
    android.util.Log.w(TAG, "sync shadow fell back to an older copy for $key — recoverArchive set")
  }

  private fun fail(e: IOException): Nothing { failure = e; throw e }

  /** فایل‌های همین کلید که هیچ نشانی به آن‌ها اشاره نمی‌کند (کوچِ بسته‌شده پیش از نشان)، دست‌کم ۱۰ دقیقه کهنه */
  private fun sweep(now: Long) {
    val named = prefs.all.values.mapNotNullTo(HashSet()) { (it as? String)?.let(Marker::parse)?.name }
    val prefix = "$FILE_PREFIX${OpQueue.fileKey(key)}-"
    (dir.listFiles().orEmpty().toList() + app.filesDir.listFiles().orEmpty().toList()).forEach { x ->
      if (!x.name.startsWith(prefix) || x.name.contains(".corrupt-")) return@forEach
      val base = x.name.substringBefore(".dat") + ".dat"
      val at = base.removePrefix(prefix).removeSuffix(".dat").toLongOrNull() ?: return@forEach
      if (base !in named && now - at > 10 * 60_000L) x.delete()
    }
  }

  private fun nameFor(g: Long) = "$FILE_PREFIX${OpQueue.fileKey(key)}-$g.dat"

  /**
   *  فایلِ نشان: در [dir]؛ ساختِ پیش از پوشه آن را در خودِ `filesDir` می‌گذاشت (داخلِ پشتیبانِ خودکار) ⇒ اول `.prev` و بعد
   *  اصل به پوشه (rename ِ همان دیسک). برق میانِ دو گام ⇒ بارِ بعد اصلِ مانده را می‌برد؛ rename نشد ⇒ همان جای پیشین
   *  برای این اجرا (هیچ چیزی گم یا دو تا نمی‌شود).
   */
  private fun located(name: String): ShadowFile {
    val here = File(dir, name)
    val old = File(app.filesDir, name)
    if (here.isFile || (!old.isFile && !File(old.path + ".prev").isFile)) return ShadowFile(here)
    for (suffix in listOf(".prev", "")) {
      val from = File(old.path + suffix)
      if (from.isFile && !from.renameTo(File(here.path + suffix))) return ShadowFile(old)
    }
    return ShadowFile(here)
  }

  private fun noteMainThread() {
    if (!onMainThread()) return
    mainThreadReads++
    android.util.Log.w(TAG, "sync shadow file read on the main thread")
  }

  /** `v1;copy|file;<نامِ فایل>;<شمارهٔ نوشتنِ کوچ>` */
  private class Marker(val copy: Boolean, val name: String, val gen: Long) {
    fun text() = "v1;${if (copy) "copy" else "file"};$name;$gen"

    companion object {
      //  نامِ ساده در [DIR] (کلیدِ `OpQueue.fileKey` حرفِ فارسی هم دارد) — نه پوشه، نه `..`
      private val NAME = Regex("""sync-shadow-[^/\\;]+\.dat""")

      fun parse(s: String): Marker? {
        val p = s.split(';')
        if (p.size != 4 || p[0] != "v1" || !NAME.matches(p[2]) || p[2].contains("..")) return null
        val copy = when (p[1]) { "copy" -> true; "file" -> false; else -> return null }
        return Marker(copy, p[2], p[3].toLongOrNull() ?: return null)
      }
    }
  }

  /** گام‌هایی که آزمونِ قطعِ برق میانشان می‌بُرد */
  enum class Step { MIGRATE_WRITTEN, MIGRATE_VERIFIED, MIGRATED, COMMIT_WRITTEN, BACK_WRITTEN }

  /** آزمون: «بسته شدنِ برنامه» در یک گام */
  class CrashForTest(val step: Step) : RuntimeException("crash at $step")

  companion object {
    private const val TAG = "ShadowStore"
    private const val PREFS = "tohid-sync-v1"
    const val MARKER = "shadowStore"
    const val FILE_PREFIX = "sync-shadow-"
    /** پوشهٔ فایل‌های سایه زیرِ `filesDir` — همین نام در `backup_rules.xml`، `backup_rules_legacy.xml` و `AtRestScope.syncState` */
    const val DIR = "sync-shadow"

    /** `filesDir/sync-shadow/` (ساخته اگر نیست) */
    fun dirOf(context: Context): File = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
    private const val NOT_WRITTEN = "سایهٔ همگام‌سازی روی دیسک ننشست"  // داده: متنِ خامِ `lastError` (ذخیره می‌شود؛ `SyncModel.classify` با نشانه‌هایش دسته می‌کند) — جزئیاتِ پشتیبانی

    private val slots = HashMap<String, ShadowStore>()

    /** یک نمونه برای هر کلید (همهٔ `SyncStateV1` ِ همان کلید همان حافظه را می‌بینند، مثلِ prefs) */
    fun of(context: Context, key: String): ShadowStore {
      val app = context.applicationContext
      return synchronized(slots) { slots.getOrPut(app.filesDir.path + "\u0000" + key) { ShadowStore(app, key) } }
    }

    /** `SyncV1Keys.move`: حالِ دو کلید روی دیسک جابه‌جا شد */
    fun invalidate(context: Context, vararg keys: String) = keys.forEach { of(context, it).invalidate() }

    /** آزمون: همهٔ حافظه‌ها، مثلِ شروعِ دوبارهٔ برنامه */
    fun restartForTest() = synchronized(slots) { slots.values.forEach { it.invalidate() } }

    /** آزمون: بریدن در یک گام (پرتابِ [CrashForTest]) */
    @Volatile var crash: ((Step) -> Unit)? = null

    /** شمارِ خواندنِ فایل روی رشتهٔ صفحه — باید صفر بماند (`ShadowStoreTest`) */
    @Volatile var mainThreadReads = 0

    /** آزمون می‌تواند «رشتهٔ صفحه» را خودش بگوید */
    @Volatile var mainThread: (() -> Boolean)? = null

    fun onMainThread(): Boolean =
      mainThread?.invoke() ?: runCatching { android.os.Looper.getMainLooper().isCurrentThread }.getOrDefault(false)

    private fun clock() = System.currentTimeMillis()

    @Volatile private var lastGen = 0L

    /** شمارهٔ کوچِ تازه (و نامِ فایلش): زمان، ولی همیشه بزرگ‌تر از پیشین — دو کوچ در یک میلی‌ثانیه یک نام نمی‌گیرند */
    @Synchronized private fun nextGen(): Long = maxOf(clock(), lastGen + 1).also { lastGen = it }
  }
}

/** همان `commitShadow`، با پرتابِ همان پیامِ همیشه (`SyncV1Engine`: Snapshot و هم‌ترازی سایه و cursor را با هم می‌نشانند) */
internal fun SyncStateV1.commitShadowOrThrow(shadow: String, cursor: Long?) {
  if (!commitShadow(shadow, cursor)) throw IOException("سایهٔ همگام‌سازی روی دیسک ننشست")  // داده: متنِ خامِ `lastError` (ذخیره می‌شود؛ `SyncModel.classify` با نشانه‌هایش دسته می‌کند) — جزئیاتِ پشتیبانی
}
