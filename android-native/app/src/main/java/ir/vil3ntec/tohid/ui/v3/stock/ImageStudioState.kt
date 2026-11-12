package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.image.ImageStudio
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ImageStudioSource
import ir.vil3ntec.tohid.ui.screens.serverRefused

/*
 *  ۴-۲۳ (۱) — حالِ صفحه‌های «استودیوی عکس» (`ImageStudioPages.kt`)، بی Compose ِ دیدنی و بی سرور ⇒ آزمون‌پذیر
 *  (`ImageStudioPagesTest`). منطقِ خواندن و درخواست در `core/image/ImageStudio`.
 */

/** فهرستِ دسته‌ها؛ ۴۰۳ (پرچمِ استودیو یا بانک خاموش) ⇒ `off` */
@Stable
class ImageStudioListState(private val src: ImageStudioSource) {
  var batches by mutableStateOf<List<ImageStudio.Summary>?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set

  suspend fun load() {
    failed = false
    when (val r = src.batches()) {
      is ApiResult.Success -> { batches = r.value; off = false }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; batches = null } else failed = true
    }
  }
}

/** «دستهٔ تازه»: عکس‌های خودِ دکان، گزینش، preset و آغاز (کلیدِ یکتایی برای همین فرم یک بار ساخته می‌شود) */
@Stable
class ImageStudioNewState(private val src: ImageStudioSource, newKey: () -> String) {
  private val key = newKey()
  var config by mutableStateOf<ImageStudio.Config?>(null)
    private set
  var photos by mutableStateOf<List<ImageStudio.Mine>?>(null)
    private set
  var next by mutableStateOf<String?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var picked by mutableStateOf<Set<String>>(emptySet())
    private set
  var preset by mutableStateOf("catalog")
  var busy by mutableStateOf(false)
    private set
  var problem by mutableStateOf<ImageStudio.Check?>(null)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  val max: Int get() = config?.batchMax ?: 100

  suspend fun load() {
    failed = false
    when (val c = src.config()) {
      is ApiResult.Success -> if (c.value == null) { off = true; return } else config = c.value
      is ApiResult.Failure -> { if (serverRefused(c.error)) off = true else failed = true; return }
    }
    more()
  }

  suspend fun more() {
    when (val r = src.mine(next.orEmpty())) {
      is ApiResult.Success -> { photos = photos.orEmpty() + r.value.first; next = r.value.second }
      is ApiResult.Failure -> if (serverRefused(r.error)) off = true else failed = true
    }
  }

  fun toggle(id: String) {
    picked = if (id in picked) picked - id else picked + id
    problem = null
  }

  /** ⇒ شناسهٔ دستهٔ ساخته‌شده (همان کلید دوباره ⇒ همان دسته)، یا null با `problem`/`error` */
  suspend fun start(): String? {
    if (busy) return null
    val ids = photos.orEmpty().map { it.id }.filter { it in picked }
    val chk = ImageStudio.check(ids, preset, max)
    if (chk != ImageStudio.Check.Ok) { problem = chk; return null }
    busy = true
    error = null
    return try {
      when (val r = src.create(key, ids, preset)) {
        is ApiResult.Success -> r.value.id
        is ApiResult.Failure -> { error = r.error.userMessage; null }
      }
    } finally { busy = false }
  }
}

/** یک دسته: پیشرفت، کارها و تأیید/ردِ هر نسخه (خطر فقط با تأییدِ جدای همان نسخه) */
@Stable
class ImageStudioBatchState(private val src: ImageStudioSource, val id: String) {
  sealed interface Outcome {
    data class Reviewed(val approved: Boolean) : Outcome
    data class Failed(val message: String) : Outcome
  }

  var batch by mutableStateOf<ImageStudio.Batch?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  var outcome by mutableStateOf<Outcome?>(null)
    private set
  /** نسخه‌ای که کاربر یک بار «تأیید» زد و خطر دارد ⇒ دکمهٔ «با وجودِ خطر» برای همان */
  var riskAsk by mutableStateOf<String?>(null)
    private set

  suspend fun load() {
    failed = false
    when (val r = src.batch(id)) {
      is ApiResult.Success -> { batch = r.value; off = false }
      is ApiResult.Failure -> if (serverRefused(r.error) && batch == null) off = true else failed = true
    }
  }

  /** صفحهٔ بعدیِ قلم‌ها */
  suspend fun more() {
    val b = batch ?: return
    val after = b.next ?: return
    when (val r = src.batch(id, after)) {
      is ApiResult.Success -> batch = r.value.copy(items = b.items + r.value.items)
      is ApiResult.Failure -> failed = true
    }
  }

  suspend fun act(action: String) {
    if (busy || batch?.actions?.contains(action) != true) return
    busy = true
    try {
      when (val r = src.action(id, action)) {
        is ApiResult.Success -> load()
        is ApiResult.Failure -> outcome = Outcome.Failed(r.error.userMessage)
      }
    } finally { busy = false }
  }

  /**
   *  تأیید/رد؛ نسخهٔ پرخطر: بارِ اول فقط `riskAsk` (بی درخواست)، و تنها `confirmRisk = true` ِ همان نسخه می‌فرستد.
   */
  suspend fun review(item: ImageStudio.Item, approve: Boolean, confirmRisk: Boolean = false) {
    val v = item.resultVersion ?: return
    if (busy || !item.reviewable) return
    if (approve && item.risk.isNotEmpty() && !(confirmRisk && riskAsk == item.imageId)) { riskAsk = item.imageId; return }
    busy = true
    try {
      when (val r = src.review(item.imageId, v, approve, approve && item.risk.isNotEmpty())) {
        is ApiResult.Success -> { if (riskAsk == item.imageId) riskAsk = null; outcome = Outcome.Reviewed(approve); load() }
        is ApiResult.Failure -> outcome = Outcome.Failed(r.error.userMessage)
      }
    } finally { busy = false }
  }

  fun dismissRisk() { riskAsk = null }
}
