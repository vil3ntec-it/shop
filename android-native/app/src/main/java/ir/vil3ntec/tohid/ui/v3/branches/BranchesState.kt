package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchKey
import ir.vil3ntec.tohid.core.branch.BranchTally
import ir.vil3ntec.tohid.core.i18n.StrBranchDesk
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.BranchDesk
import ir.vil3ntec.tohid.core.stock.BranchStock
import ir.vil3ntec.tohid.data.repo.BranchRulesSource
import ir.vil3ntec.tohid.data.repo.ShopBranchesSource
import ir.vil3ntec.tohid.ui.screens.serverRefused

/*
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md` §۸): حالِ «انتخابِ شعبه»، صفحهٔ «شعبه‌ها» ِ صاحب و گزارشِ
 *  هر شعبه و جمع — همه بی سرور آزموده می‌شوند (`BranchesStateTest`). صفحه‌ها در `BranchesPagesAndroid.kt`.
 *  ⛔ پرچمِ `shop.branches` خاموش ⇒ پاسخِ بی `enabled` یا ۴۰۴ ⇒ [BranchLive.book] null ⇒ هیچ راه، ردیف یا پرسشی.
 */

/** آخرین دفترچهٔ شعبه‌های همین عضو و پرسشِ باز */
object BranchLive {
  /** null ⇒ خاموش یا هنوز نپرسیده ⇒ هیچ چیزِ تازه دیده نمی‌شود */
  var book by mutableStateOf<BranchBook.Book?>(null)
    private set

  /** پرسشِ باز پس از ورود (`Ask`/`None`)؛ null ⇒ هیچ */
  var pick by mutableStateOf<BranchBook.Pick?>(null)

  /** شعبهٔ دفترِ روی میز برای نمایش (`BranchDesk.current` ِ آخرین پرسش یا تعویض) */
  var current by mutableStateOf(BranchKey.MAIN)

  /** فازِ ۳: کالا/دسته/واحد در شعبهٔ روی میز فقط‌خواندنی است (hello ِ v3) ⇒ ویرایشِ کالا بسته (`BranchCatalogGate`) */
  var catalogReadOnly by mutableStateOf(false)

  /** آخرین پاسخِ روشنِ سرور: شعبه‌ها خاموش (پرچم یا سرورِ کهنه) — میزِ شعبهٔ دیگر به اصلی برمی‌گردد */
  var off by mutableStateOf(false)
    private set

  /** نامِ شعبهٔ جاری برای نمایش */
  fun nameOf(id: String): String {
    val b = book?.branches?.firstOrNull { it.id == id }
    return ir.vil3ntec.tohid.core.i18n.branchName(b?.name.orEmpty(), BranchKey.isMain(id))
  }

  fun label(b: BranchBook.Branch): String = ir.vil3ntec.tohid.core.i18n.branchName(b.name, b.main)

  /** صفحهٔ «شعبه‌ها» و ردیفِ «حسابِ من» فقط وقتی سرور روشن گفت و این عضو بیش از یک شعبه دارد یا صاحب است */
  val visible: Boolean get() = book?.let { it.owner || it.choices.size > 1 } == true

  fun reset() {
    book = null
    pick = null
    off = false
    current = BranchKey.MAIN
    catalogReadOnly = false
  }

  /**
   *  `GET /shop/branches` ⇐ تصمیم (`BranchBook.decide`). بی‌اینترنت ⇒ همان پیشین و null؛ ردِ سرور یا خاموش ⇒ [off].
   *  [afterSignIn] ⇒ با بیش از یک شعبه همیشه بپرس.
   */
  suspend fun probe(src: ShopBranchesSource, current: String, afterSignIn: Boolean): BranchBook.Pick? =
    when (val r = src.book()) {
      is ApiResult.Success -> when (val a = r.value) {
        BranchBook.Answer.Off -> { book = null; pick = null; off = true; null }
        is BranchBook.Answer.On -> {
          book = a.book
          off = false
          BranchBook.decide(a.book, current, afterSignIn).also { pick = it.takeIf { p -> p is BranchBook.Pick.Ask || p is BranchBook.Pick.None } }
        }
      }
      is ApiResult.Failure -> {
        if (serverRefused(r.error)) { book = null; pick = null; off = true }
        null
      }
    }

  /** پرسش را می‌شود بست؟ فقط وقتی شعبهٔ جاری هنوز در دسترس است */
  fun dismissible(current: String): Boolean = book?.choices?.any { it.id == current } == true

  /** پیامِ پایانِ تعویض؛ null ⇒ هیچ */
  fun outcomeText(o: BranchDesk.Outcome, name: String): String? = when (o) {
    BranchDesk.Outcome.SAME -> null
    BranchDesk.Outcome.SWITCHED -> StrBranchDesk.switched(name)
    BranchDesk.Outcome.NO_SHOP -> StrBranchDesk.noShop
    BranchDesk.Outcome.TRAINING -> StrBranchDesk.training
    BranchDesk.Outcome.PENDING -> StrBranchDesk.pending
    BranchDesk.Outcome.V3_OFF -> StrBranchDesk.v3Off
    BranchDesk.Outcome.NOT_ARCHIVED -> StrBranchDesk.notArchived
  }

  /** شعبهٔ اصلی برای برگرداندنِ میز وقتی شعبه‌ها خاموش شد */
  val main: BranchBook.Branch get() = BranchBook.Branch(BranchKey.MAIN, "", archived = false, main = true)
}

/**
 *  صفحهٔ «شعبه‌ها»: دفترچه، اعضا (فقط صاحب)، نوشتن‌ها با پیامِ روشن. [rules] (فازِ ۴، فقط صاحب): کلیدهای شعبه‌ایِ دکان
 *  (`staffOtherStock`، `transferNegative`) و مجوزِ «موجودیِ شعبه‌های دیگر» ِ هر عضو (POS-16/S17).
 */
@Stable
class BranchesPageState(private val src: ShopBranchesSource, private val rules: BranchRulesSource? = null) {
  var book by mutableStateOf<BranchBook.Book?>(null)
    private set
  var members by mutableStateOf<List<BranchBook.Member>>(emptyList())
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  /** کلیدهای شعبه‌ایِ دکان (فقط صاحب و فقط با [rules]؛ null ⇒ هنوز نه) */
  var settings by mutableStateOf<BranchStock.Settings?>(null)
    private set
  /** پیامِ آخرین نوشتن (ذخیره شد / ذخیره نشد: …) */
  var message by mutableStateOf<String?>(null)
    private set

  suspend fun load() {
    busy = true
    failed = false
    when (val r = src.book()) {
      is ApiResult.Success -> when (val a = r.value) {
        BranchBook.Answer.Off -> { off = true; book = null }
        is BranchBook.Answer.On -> { off = false; book = a.book }
      }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; book = null } else failed = true
    }
    if (book?.owner == true) {
      when (val m = src.members()) {
        is ApiResult.Success -> members = m.value.members
        is ApiResult.Failure -> failed = true
      }
      rules?.let { r -> (r.settings() as? ApiResult.Success)?.let { settings = it.value } }
    } else {
      members = emptyList()
      settings = null
    }
    busy = false
  }

  /** کلیدِ «شاگردان موجودیِ شعبه‌های دیگر را ببینند» */
  suspend fun setStaffOtherStock(on: Boolean): Boolean = rules?.let { r -> write { r.saveSettings(BranchStock.settingsBody(staffOtherStock = on)) } } == true

  /** کلیدِ «ارسالِ بیش از موجودی» */
  suspend fun setTransferNegative(on: Boolean): Boolean = rules?.let { r -> write { r.saveSettings(BranchStock.settingsBody(transferNegative = on)) } } == true

  /** مجوزِ «موجودیِ شعبه‌های دیگر» ِ یک عضو (صاحب و مدیر همیشه دارند — دکمه‌اش فقط برای بقیه) */
  suspend fun grantOtherStock(memberId: String, on: Boolean): Boolean = rules?.let { r -> write { r.grant(memberId, on) } } == true

  /** آیا دکمهٔ مجوزِ عضو لازم است؟ صاحب و مدیر همیشه می‌بینند */
  fun needsGrant(m: BranchBook.Member): Boolean = m.role !in BranchStock.ALWAYS_ROLES

  fun granted(m: BranchBook.Member): Boolean = BranchStock.PERM in m.perms

  private suspend fun write(block: suspend () -> ApiResult<*>): Boolean {
    if (busy) return false
    busy = true
    val r = block()
    busy = false
    message = when (r) {
      is ApiResult.Success -> StrBranchDesk.saved
      is ApiResult.Failure -> StrBranchDesk.notSaved(r.error.userMessage)
    }
    if (r is ApiResult.Success) load()
    return r is ApiResult.Success
  }

  /** نامِ تهی ⇒ هیچ (سرور هم رد می‌کند) */
  suspend fun create(name: String): Boolean = name.isNotBlank() && write { src.create(name) }

  suspend fun rename(id: String, name: String): Boolean = name.isNotBlank() && write { src.update(id, name, null) }

  /** شعبهٔ اصلی بایگانی نمی‌شود */
  suspend fun setArchived(id: String, archived: Boolean): Boolean = !(archived && BranchKey.isMain(id)) && write { src.update(id, null, archived) }

  /** [branches] تهی ⇒ فقط اصلی (همان پیش‌فرضِ سرور) */
  suspend fun setMember(memberId: String, branches: List<String>): Boolean = write { src.setMember(memberId, branches.distinct()) }

  /** شعبه‌های تازهٔ عضو پس از زدن/برداشتنِ [branch] */
  fun toggled(member: BranchBook.Member, branch: String): List<String> {
    val now = member.branches.ifEmpty { listOf(BranchKey.MAIN) }
    val next = if (branch in now) now - branch else now + branch
    return next.ifEmpty { listOf(BranchKey.MAIN) }
  }
}

/** گزارشِ هر شعبه و «جمعِ همه» (`GET /shop/branches/report`) */
@Stable
class BranchReportState(private val src: ShopBranchesSource) {
  var days by mutableStateOf(BranchTally.SPANS.first())
  var view by mutableStateOf<BranchTally.View?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set

  suspend fun load(todayIso: String) {
    busy = true
    failed = false
    when (val r = src.report(days, todayIso)) {
      is ApiResult.Success -> { view = r.value; off = r.value == null }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; view = null } else failed = true
    }
    busy = false
  }
}
