package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ImageChangesSource
import ir.vil3ntec.tohid.ui.screens.serverRefused

/*
 *  IMG-PRO-17 — حالِ صفحه‌های «تغییرِ دسته‌ایِ عکس‌ها» (`ImageChangesPages.kt`)، بی Compose ِ دیدنی و بی سرور ⇒ آزمون‌پذیر
 *  (`ImageChangesPagesTest`). منطقِ «کدام کالا با کدام کار» در `core/image/ImageChanges`.
 */

/** «دیده شود؟» برای راهِ «تغییرِ دسته‌ایِ عکس‌ها» در «…» ِ انبار: بانکِ روشن (`ImageBank.enabled`) و نقشِ غیرِ شاگرد */
object ImageChangesGate {
  fun visible(bankOn: Boolean?, staff: Boolean): Boolean = bankOn == true && !staff
}

/** فهرستِ دسته‌ها (تازه‌ترین بالا) */
@Stable
class ImageChangesListState(private val src: ImageChangesSource) {
  var changes by mutableStateOf<List<ImageChanges.Change>?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set

  suspend fun load() {
    failed = false
    when (val r = src.list()) {
      is ApiResult.Success -> { changes = r.value; off = false }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; changes = null } else failed = true
    }
  }
}

/** یک دسته و «برگرداندنِ همه» */
@Stable
class ImageChangeDetailState(private val src: ImageChangesSource, val id: String) {
  sealed interface Outcome {
    data object Done : Outcome
    data object Already : Outcome
    /** ۴۰۹ `changed_since`: هیچ چیز برنگشت؛ این کالاها پس از دسته دوباره عوض شدند */
    data class ChangedSince(val products: List<String>) : Outcome
    data class Failed(val message: String) : Outcome
  }

  var change by mutableStateOf<ImageChanges.Change?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  var outcome by mutableStateOf<Outcome?>(null)
    private set

  suspend fun load() {
    failed = false
    when (val r = src.detail(id)) {
      is ApiResult.Success -> { change = r.value; off = false }
      is ApiResult.Failure -> if (serverRefused(r.error) && change == null) off = true else failed = true
    }
  }

  /** یا همه یا هیچ؛ تکرار (پس از قطعِ شبکه) ⇒ «پیش‌تر برگشته بود» */
  suspend fun rollback(reason: String) {
    if (busy) return
    busy = true
    outcome = when (val r = src.rollback(id, reason)) {
      is ApiResult.Success -> {
        change = r.value.first
        if (r.value.second) Outcome.Already else Outcome.Done
      }
      is ApiResult.Failure -> {
        val f = r.error
        if (f.code == "changed_since") Outcome.ChangedSince(ImageChanges.problem(f.code, f.detail).products)
        else Outcome.Failed(f.userMessage)
      }
    }
    busy = false
  }

  fun clearOutcome() { outcome = null }
}

/** دستهٔ تازه: کار، کالاها، بررسی (کدام کالا عوض می‌شود و کدام با چه دلیلی کنار می‌ماند)، و فرستادن */
@Stable
class ImageBatchState(private val src: ImageChangesSource, private val newKey: () -> String) {
  var action by mutableStateOf(ImageChanges.Action.LATEST_CENTRAL)
    private set
  var selected by mutableStateOf<List<String>>(emptyList())
    private set
  var planned by mutableStateOf<List<ImageChanges.Planned>?>(null)
    private set
  var checking by mutableStateOf(false)
    private set
  var sending by mutableStateOf(false)
    private set
  /** کالاهایی که کاربر تطبیقِ نامطمئنشان را یکی‌یکی تأیید کرد */
  var confirmed by mutableStateOf<Set<String>>(emptySet())
    private set
  var problem by mutableStateOf<ImageChanges.Problem?>(null)
    private set
  var message by mutableStateOf<String?>(null)
    private set
  var done by mutableStateOf<ImageChanges.Change?>(null)
    private set
  /** کلیدِ تکرارِ همین دسته: تا نشستن ثابت (تکرار پس از قطعِ شبکه همان دسته)، پس از نشستن تازه */
  private var key: String? = null

  val lines: List<ImageChanges.Line> get() = planned.orEmpty().filterIsInstance<ImageChanges.Planned.Send>().map { it.line }

  fun choose(a: ImageChanges.Action) {
    if (a == action) return
    action = a
    planned = null
    problem = null
    message = null
  }

  /** ⇒ false اگر سقفِ سرور (۱۰۰ کالا) پر است */
  fun toggle(id: String): Boolean {
    val now = selected
    if (id in now) { selected = now - id; planned = null; return true }
    if (now.size >= ImageChanges.MAX_ITEMS) return false
    selected = now + id
    planned = null
    return true
  }

  /** وضعِ هر کالای انتخاب‌شده از سرور، به ترتیبِ انتخاب ⇒ نقشه */
  suspend fun check(products: Map<String, ImageChanges.Product>) {
    if (checking) return
    checking = true
    problem = null
    message = null
    val out = ArrayList<ImageChanges.Planned>()
    for (id in selected) {
      val p = products[id] ?: continue
      val cur = when (val r = src.current(id)) {
        is ApiResult.Success -> r.value
        is ApiResult.Failure -> {
          if (serverRefused(r.error) && r.error.code == ImageChanges.OFF) { message = r.error.userMessage; checking = false; return }
          null
        }
      }
      out += ImageChanges.plan(action, p, cur)
    }
    planned = out
    checking = false
  }

  fun confirm(productId: String) {
    confirmed = confirmed + productId
    problem = null
    message = null
  }

  /** کالای مسئله‌دار را از دسته برمی‌دارد (بارکدِ متفاوت، عکسِ دیگر انتخاب‌نشدنی…) */
  fun remove(productId: String) {
    selected = selected - productId
    planned = planned?.filterNot {
      (it as? ImageChanges.Planned.Send)?.line?.productId == productId || (it as? ImageChanges.Planned.Skipped)?.productId == productId
    }
    confirmed = confirmed - productId
    problem = null
    message = null
  }

  suspend fun send(note: String) {
    if (sending) return
    val l = lines
    if (l.isEmpty()) return
    sending = true
    problem = null
    message = null
    val k = key ?: newKey().also { key = it }
    when (val r = src.apply(ImageChanges.body(k, note, l, confirmed))) {
      is ApiResult.Success -> { done = r.value; key = null }
      is ApiResult.Failure -> {
        val f: ApiFailure = r.error
        //  خطای قلم (۴۰۰/۴۰۴/۴۰۹): تراکنشِ سرور برگشت ⇒ هیچ کالایی عوض نشد؛ کلیدِ تکرارِ تازه فقط پس از `key_reused`
        if (f.code == "key_reused") key = null
        problem = if (f.retryable) null else ImageChanges.problem(f.code, f.detail, l)
        message = f.userMessage
      }
    }
    sending = false
  }
}
