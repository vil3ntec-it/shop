package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.sync.SyncAccountKey
import ir.vil3ntec.tohid.platform.ServerSwitchAndroid
import ir.vil3ntec.tohid.sync.SyncStore
import java.io.File

/**
 *  کلیدِ Sync v1 (صف، سایه، cursor، ژورنال، دفترچهٔ بازپخش) = **شناسهٔ حسابی که وارد شد** — در هر راهِ ورود.
 *
 *  ── اشکالی که این می‌بندد (ریشهٔ «خروج زدم، همه‌چیز پاک شد») ─────────────
 *  `SyncStateV1.currentAccount` فقط در برگهٔ «کدِ ایمیلی» نوشته می‌شد؛ ورودِ رمز، کد، گوگل، اپل و **کدِ شاگرد**
 *  آن را دست نمی‌زدند و کلید یا شناسهٔ حسابِ پیشین می‌ماند یا نامِ نمایشی (که خروج پاکش می‌کرد). پس شاگردی که
 *  روی گوشیِ صاحب وارد می‌شد دفترِ خالیِ خودش را با سایه و cursor ِ صاحب داشت: Snapshot هرگز گرفته نمی‌شد و
 *  نخستین ذخیره برای هر ردیفِ صاحب DELETE می‌ساخت. شرح سرِ `core/sync/AccountSafety.kt`.
 *  ──────────────────────────────────────────────────────────────────────────
 *
 *  حالِ کهنهٔ **همین حساب** (زیرِ نامِ نمایشیِ پیشینش) یک بار به کلیدِ تازه می‌رود — صف با همان op_id، سایه،
 *  cursor و ژورنال — تا هیچ تغییرِ نرفته‌ای گم یا دو بار فرستاده نشود (`SyncAccountKey.carryFrom`).
 */
object SyncV1Keys {

  private const val PREFS = "tohid-sync-v1"

  /** کلیدِ دامنه‌دارِ یک نامِ خام — همان `SyncV1Engine.keyOf` */
  fun scoped(context: Context, raw: String): String = ServerSwitchAndroid.scopedKey(context.applicationContext, raw)

  /** آیا این کلید هیچ ردی از همگام‌سازی دارد (cursor، سایه، هم‌ترازی، صف) */
  fun hasState(context: Context, key: String): Boolean {
    //  بی خواندنِ فایلِ سایه (`ShadowStore.present`): نشانِ فایل خودش رد است
    if (SyncStateV1(context, key).hasSyncState()) return true
    return runCatching { OpQueue(context, key).size() > 0 }.getOrDefault(false)
  }

  /**
   *  پس از هر ورودِ موفق، **پس از** `LedgerOwner` و پیش از نخستین همگام‌سازی.
   *
   *  @param userId شناسهٔ حسابِ واردشده (خالی ⇒ هیچ کاری)
   *  @param display نامِ نمایشیِ همین حساب — کلیدِ کهنهٔ ورودِ رمز/کدِ شاگرد پیش از این اصلاح
   *  @param sameDesk دفترِ روی میز از پیش مالِ همین حساب بود
   *  @return کلیدِ تازه
   */
  fun bind(context: Context, userId: String, display: String, sameDesk: Boolean): String {
    val app = context.applicationContext
    val user = userId.trim()
    if (user.isBlank()) return SyncV1Engine.of(app).currentKey()
    val engine = SyncV1Engine.of(app)
    val target = scoped(app, user)
    synchronized(engine) {
      val previous = SyncStateV1.currentAccount(app)
      val deviceKey = scoped(app, SyncStore(app).deviceUid)
      val from = SyncAccountKey.carryFrom(
        target = target,
        targetHasState = hasState(app, target),
        sameDesk = sameDesk,
        previousCurrent = previous.takeIf { it.isNotBlank() }?.let { scoped(app, it) }.orEmpty(),
        candidates = listOf(display.trim(), SyncStore(app).accountName.trim())
          .filter { it.isNotBlank() }.map { scoped(app, it) } + engine.currentKey(),
        deviceKey = deviceKey,
      ) { hasState(app, it) }
      if (from != null) move(app, from, target)
      SyncStateV1.setCurrentAccount(app, user)
    }
    engine.rebind()
    return target
  }

  /**
   *  همان حساب از دکانی به دکانِ دیگر (`LedgerOwner`: SWITCHED با همان حساب — مثلاً شاگردی که با کدِ دکانِ دیگر
   *  می‌پیوندد): حالِ همگام‌سازیِ دکانِ پیشین کنارِ دفترش بایگانی می‌شود (`<حساب>@<دکان>`) و حالِ دکانِ تازه (اگر
   *  روی این دستگاه سابقه داشت) باز. بی این، دفترِ خالیِ دکانِ تازه با سایه و cursor ِ دکانِ پیشین کار می‌کرد:
   *  Snapshot نمی‌آمد و نخستین ذخیره برای ردیف‌های دکانِ پیشین DELETE می‌ساخت.
   */
  fun switchShop(context: Context, userId: String, fromShop: String, toShop: String) {
    val app = context.applicationContext
    if (userId.isBlank() || fromShop == toShop) return
    val engine = SyncV1Engine.of(app)
    //  کلیدی که همین دفتر با آن کار می‌کرد (شناسهٔ حساب، یا نامِ نمایشیِ ساخت‌های پیشین)
    val live = engine.currentKey()
    synchronized(engine) {
      if (hasState(app, live)) move(app, live, scoped(app, "$userId@$fromShop"))
      val back = scoped(app, "$userId@$toShop")
      if (toShop.isNotBlank() && hasState(app, back)) move(app, back, live)
    }
    engine.rebind(force = true)
  }

  /**
   *  جابه‌جاییِ کاملِ حالِ یک کلید به کلیدِ دیگر: ترجیحات (`<کلید>.*`)، صف و ردشده‌هایش، ژورنال و دفترچهٔ بازپخش.
   *  اول رونوشت، بعد پاک کردنِ اصل؛ مقصدی که از پیش فایل دارد بازنویسی نمی‌شود. فایلِ سایه (`ShadowStore`) جابه‌جا
   *  نمی‌شود: نامش در نشانِ `<کلید>.shadowStore` است و نشان با همین یک `commit` ِ prefs می‌رود.
   */
  internal fun move(context: Context, from: String, to: String) {
    if (from == to) return
    val app = context.applicationContext
    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val edit = prefs.edit()
    val gone = ArrayList<String>()
    for ((k, v) in prefs.all) {
      if (!k.startsWith("$from.")) continue
      val name = "$to." + k.removePrefix("$from.")
      when (v) {
        is String -> edit.putString(name, v)
        is Long -> edit.putLong(name, v)
        is Int -> edit.putInt(name, v)
        is Boolean -> edit.putBoolean(name, v)
        is Float -> edit.putFloat(name, v)
        else -> continue
      }
      gone += k
    }
    gone.forEach { edit.remove(it) }
    edit.commit()
    ShadowStore.invalidate(app, from, to)

    val dir = app.filesDir
    val a = OpQueue.fileKey(from)
    val b = OpQueue.fileKey(to)
    listOf(
      "oplog-$a.json" to "oplog-$b.json",
      "oplog-$a-dropped.json" to "oplog-$b-dropped.json",
      "oplog-$a-resend.json" to "oplog-$b-resend.json",  // دفترچهٔ `DroppedResend` همراهِ همان دفترِ ردشده
      SetAsideMarks.v1Name(a) to SetAsideMarks.v1Name(b),
      "sync-journal-$a.json" to "sync-journal-$b.json",
      ir.vil3ntec.tohid.core.sync.ReplayBook.fileName("sync-replay-$a") to
        ir.vil3ntec.tohid.core.sync.ReplayBook.fileName("sync-replay-$b"),
    ).forEach { (x, y) ->
      val src = File(dir, x)
      val dst = File(dir, y)
      if (!src.exists() || dst.exists()) return@forEach
      runCatching { src.copyTo(dst, overwrite = false) }.onSuccess { src.delete() }
    }
  }
}
