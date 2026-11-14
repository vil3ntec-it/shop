package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchKey
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.server.DataPartition
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner

/**
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md`): دفترِ روی میز مالِ **کدام شعبه** است.
 *
 *  `LedgerOwner` می‌گوید دفتر مالِ کدام حساب@دکان است؛ این، شعبهٔ همان را (همان پروندهٔ ترجیحات، کلیدِ جدا). نامِ
 *  بایگانیِ دفترِ هر شعبه `BranchKey.vaultKey(<حساب@دکان>, شعبه)` است و حال و صفِ Sync v3 ِ هر شعبه هم جدا
 *  (`SyncV3Runner`، `BranchKey.syncFileKey`) ⇒ **دادهٔ دو شعبه هرگز در یک دفتر یا یک صف نمی‌نشیند**.
 *
 *  ⛔ شعبهٔ اصلی = شناسهٔ خالی = همان امروز: همان بایگانی، همان کلیدِ همگام‌سازی، **بی سرآیند**. دستگاهی که هرگز
 *     شعبه‌ای برنگزید (و هر دکانی با پرچمِ خاموشِ `shop.branches`) هیچ فرقی نمی‌بیند.
 *  ⛔ شعبهٔ دیگر ⇒ سرآیندِ `X-Branch-Id` روی **هر** درخواستِ حساب‌دار ([headers]، `Backend` ⇐ `identity`): Sync v3 در بخشِ
 *     همان شعبه می‌نویسد و هر درِ «فقط اصلی» (v1، راهِ قدیم، پشتیبانِ کامل، `/shop/*` ِ شعبه‌نشناس) ۴۰۳
 *     `branch_unsupported` می‌دهد (`server/src/lib/branch-gate.js`) — دفترِ شعبه هرگز در اصلی نمی‌نشیند.
 *  ⛔ شعبهٔ دیگر فقط با Sync v3: دستگاهِ v1 فقط با صفِ خالی و سرورِ دارای v3 به v3 می‌رود ([switchTo])، و `SyncChoice`
 *     تا دفترِ روی میز شعبهٔ دیگر است به v1 برنمی‌گردد.
 *  ⛔ حساب، دکان یا سرورِ دیگر ⇒ دفترِ شعبهٔ روی میز زیرِ نامِ همان شعبه بایگانی و میز «اصلیِ» صاحبِ تازه ([reset]).
 */
object BranchDesk {
  private const val PREFS = "tohid-ledger"
  private const val ID = "owner_branch_id"
  private const val NAME = "owner_branch_name"
  /** «حساب@دکان» ای که پس از ورودش شعبه برگزیده شد (یا یک‌شعبه‌ای بود) — «انتخابِ شعبه» فقط یک بار پس از ورود */
  private const val PICKED = "owner_branch_picked"

  @Volatile private var cached: String? = null

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** شعبهٔ دفترِ روی میز؛ هرگز ثبت‌نشده یا بدشکل ⇒ اصلی */
  fun current(context: Context): String = cached ?: runCatching {
    BranchKey.idOf(prefs(context).getString(ID, "").orEmpty()) ?: BranchKey.MAIN
  }.getOrDefault(BranchKey.MAIN).also { cached = it }

  /** نامِ شعبهٔ روی میز (آخرین پاسخِ سرور)؛ خالی ⇒ هنوز نه */
  fun name(context: Context): String = runCatching { prefs(context).getString(NAME, "").orEmpty() }.getOrDefault("")

  /** سرآیندهای درخواستِ حساب‌دار: اصلی ⇒ هیچ (همان امروز) */
  fun headers(context: Context): Map<String, String> = BranchKey.headers(current(context))

  /** نامِ بایگانیِ دفترِ روی میز از نامِ بایگانیِ حساب@دکان ([base]) */
  fun vaultOf(context: Context, base: String): String = BranchKey.vaultKey(base, current(context))

  internal fun remember(context: Context, id: String, name: String) {
    prefs(context).edit().putString(ID, id).putString(NAME, name).commit()
    cached = id
  }

  /** میزِ صاحبِ تازه (حساب، دکان یا سرورِ دیگر) همیشه شعبهٔ اصلی است و «انتخابِ شعبه» دوباره پرسیده می‌شود */
  internal fun reset(context: Context) {
    if (BranchKey.isMain(current(context)) && name(context).isEmpty() && pickedFor(context).isEmpty()) return
    prefs(context).edit().putString(ID, BranchKey.MAIN).putString(NAME, "").remove(PICKED).commit()
    cached = BranchKey.MAIN
  }

  /** صاحبِ میز به شکلِ «حساب@دکان» (`LedgerOwner`)؛ بی حساب یا دکان ⇒ خالی */
  fun ownerOf(context: Context): String {
    val user = LedgerOwner.currentUser(context)
    val shop = LedgerOwner.currentShop(context)
    return if (user.isBlank() || shop.isBlank()) "" else "$user@$shop"
  }

  fun pickedFor(context: Context): String = runCatching { prefs(context).getString(PICKED, "").orEmpty() }.getOrDefault("")

  fun markPicked(context: Context) {
    val owner = ownerOf(context)
    if (owner.isNotEmpty() && pickedFor(context) != owner) prefs(context).edit().putString(PICKED, owner).apply()
  }

  /** فقط برای آزمون */
  internal fun forgetCache() { cached = null; confirmedFor = ""; confirmedAt = 0L; offSeen = false }

  enum class Outcome { SAME, SWITCHED, NO_SHOP, TRAINING, PENDING, V3_OFF, NOT_ARCHIVED }

  /**
   *  میز را به شعبهٔ [target] می‌برد: دفترِ شعبهٔ فعلی زیرِ نامِ خودش بایگانی، دفترِ [target] باز (یا دفترِ خالی)، سبدِ
   *  نیمه‌کاره پاک. همه زیرِ «دورِ» Sync v3 (`SyncV3Runner.exclusive`) تا هیچ push/pull ِ شعبهٔ پیشین با سرآیندِ شعبهٔ
   *  تازه نرود. هر گامی ننشست ⇒ هیچ چیزی عوض نمی‌شود.
   */
  suspend fun switchTo(context: Context, store: ShopStore, target: BranchBook.Branch): Outcome {
    val app = context.applicationContext
    val from = current(app)
    if (target.id == from) {
      if (name(app) != target.name) remember(app, from, target.name)
      return Outcome.SAME
    }
    if (TrainingMode.on || store.training) return Outcome.TRAINING
    val user = LedgerOwner.currentUser(app)
    val shop = LedgerOwner.currentShop(app)
    if (user.isBlank() || shop.isBlank()) return Outcome.NO_SHOP
    if (!BranchKey.isMain(target.id) && !SyncChoice.isV3(app)) {
      if (SyncV1Engine.of(app).queueSize() > 0) return Outcome.PENDING
      if (!v3Ready(app)) return Outcome.V3_OFF
      if (SyncChoice.set(app, SyncChoice.V3, SyncV1Engine.of(app).queueSize()).isNotEmpty()) return Outcome.PENDING
    }
    return SyncV3Runner.of(app).exclusive {
      val base = DataPartition.keyIn(ir.vil3ntec.tohid.platform.ServerSwitchAndroid.scope(app), user, shop)
      if (!store.stashTo(BranchKey.vaultKey(base, from))) return@exclusive Outcome.NOT_ARCHIVED
      remember(app, target.id, target.name)
      store.openFrom(BranchKey.vaultKey(base, target.id))
      runCatching { CartStore(app).clear() }
      runCatching { SyncV1Engine.of(app).publish() }
      Outcome.SWITCHED
    }
  }

  /** هر دقیقه یک بار بس: پرچمِ دکان دیرتر از این خاموش دیده نمی‌شود */
  const val CONFIRM_TTL_MS = 60_000L
  @Volatile private var confirmedFor = ""
  @Volatile private var confirmedAt = 0L
  /** آخرین پرسش گفت شعبه‌ها روشن نیست (پرچمِ خاموش یا سرورِ کهنه) ⇒ صفحه میز را به اصلی برمی‌گرداند */
  @Volatile var offSeen: Boolean = false
    private set

  /**
   *  پیش از هر دورِ Sync v3 ِ شعبهٔ دیگر: سرور هنوز شعبه‌ها را روشن دارد و همین شعبه در دسترسِ همین عضو است؟
   *  ⛔ با پرچمِ خاموش سرور سرآیند را نادیده می‌گیرد و v3 را در **بخشِ اصلی** می‌نشاند (فازِ ۱، آزمونِ «پرچم دوباره
   *  خاموش») ⇒ دفترِ شعبه نباید آن‌گاه برود. نپرسیده/بی‌اینترنت/خاموش/بایگانی ⇒ `false` (دور نمی‌رود؛ صف می‌ماند).
   *  اصلی ⇒ همیشه `true` بی هیچ درخواست (همان امروز).
   */
  suspend fun confirm(context: Context): Boolean {
    val app = context.applicationContext
    val b = current(app)
    if (BranchKey.isMain(b)) return true
    val now = System.nanoTime() / 1_000_000
    if (confirmedFor == b && now - confirmedAt in 0 until CONFIRM_TTL_MS) return true
    val ok = try {
      when (val a = BranchBook.of(Backend.api(app).get(BranchBook.PATH))) {
        BranchBook.Answer.Off -> { offSeen = true; false }
        is BranchBook.Answer.On -> { offSeen = false; a.book.choices.any { it.id == b } }
      }
    } catch (f: ApiFailure) {
      if (f is ApiFailure.NotFound) offSeen = true
      false
    }
    confirmedFor = if (ok) b else ""
    confirmedAt = if (ok) now else 0L
    return ok
  }

  /** پاسخِ صفحه (`BranchLive.probe`) همان پرسش است — دورِ بعد دوباره نپرسد */
  internal fun confirmed(id: String, on: Boolean) {
    confirmedFor = if (on) id else ""
    confirmedAt = if (on) System.nanoTime() / 1_000_000 else 0L
    offSeen = !on
  }

  /** سرور Sync v3 را دارد؟ (`SYNC_V3_ENABLED`؛ خاموش ⇒ همان ۴۰۴ ِ سرورِ قدیمی) */
  private suspend fun v3Ready(app: Context): Boolean = try {
    Backend.api(app).get("/sync/v3/hello")
    true
  } catch (f: ApiFailure) {
    false
  }
}
