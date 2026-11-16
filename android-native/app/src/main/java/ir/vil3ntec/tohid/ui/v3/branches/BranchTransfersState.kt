package ir.vil3ntec.tohid.ui.v3.branches

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrBranchTransfers
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Direction
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import ir.vil3ntec.tohid.data.repo.BranchTransfersSource
import ir.vil3ntec.tohid.ui.screens.serverRefused

/*
 *  INV-11/S21 — حالِ صفحه‌های «انتقال میانِ شعبه‌ها» (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰)، جدا از صفحه تا بی سرور آزموده شود
 *  (`BranchTransfersStateTest`). گوشی (`BranchTransfersPages.kt`) و کامپیوتر (`DeskBranchTransfers.kt`) همین را می‌خوانند.
 *  ⛔ داورِ هر گام سرور است: این‌جا فقط پیش‌سنجیِ `core/stock/BranchTransfer`، فرستادن، و پس از هر پاسخِ روشن [onChanged]
 *     (آینهٔ دفترِ دستگاه، `data/BranchTransferBookAndroid.kt`). شعبهٔ کننده [here] = شعبهٔ میز.
 */

/** نوشتهٔ هر وضعیت، گام و ردِ پیش‌سنجی (جدولِ `StrBranchTransfers` فقط `val` دارد — آزمون‌های زبان هر تابع را با عدد/متن صدا می‌زنند) */
object BranchTransferText {
  fun status(s: Status): String = when (s) {
    Status.DRAFT -> StrBranchTransfers.statusDraft
    Status.IN_TRANSIT -> StrBranchTransfers.statusInTransit
    Status.PARTIAL -> StrBranchTransfers.statusPartial
    Status.RECEIVED -> StrBranchTransfers.statusReceived
    Status.REJECTED -> StrBranchTransfers.statusRejected
    Status.CANCELLED -> StrBranchTransfers.statusCancelled
  }

  fun step(kind: String): String = when (kind) {
    "draft" -> StrBranchTransfers.stepDraft
    "edit" -> StrBranchTransfers.stepEdit
    "send" -> StrBranchTransfers.stepSend
    "receive" -> StrBranchTransfers.stepReceive
    "reject" -> StrBranchTransfers.stepReject
    "cancel" -> StrBranchTransfers.stepCancel
    else -> StrBranchTransfers.history
  }

  fun problem(p: BranchTransfer.Problem): String = when (p) {
    BranchTransfer.Problem.NO_DESTINATION -> StrBranchTransfers.pNoDestination
    BranchTransfer.Problem.SAME_BRANCH -> StrBranchTransfers.pSameBranch
    BranchTransfer.Problem.BAD_LOCATION -> StrBranchTransfers.pBadLocation
    BranchTransfer.Problem.EMPTY, BranchTransfer.Problem.NO_PRODUCT -> StrBranchTransfers.pEmpty
    BranchTransfer.Problem.TOO_MANY -> StrBranchTransfers.pTooMany
    BranchTransfer.Problem.BAD_QTY -> StrBranchTransfers.pBadQty
    BranchTransfer.Problem.DUPLICATE -> StrBranchTransfers.pDuplicate
    BranchTransfer.Problem.BAD_KEY -> StrBranchTransfers.pBadKey
    BranchTransfer.Problem.REASON_REQUIRED -> StrBranchTransfers.pReasonRequired
    BranchTransfer.Problem.OVER_RECEIVED -> StrBranchTransfers.pOverReceived
    BranchTransfer.Problem.NOT_IN_TRANSFER -> StrBranchTransfers.pNotInTransfer
    BranchTransfer.Problem.NOTHING_RECEIVED -> StrBranchTransfers.pNothingReceived
    BranchTransfer.Problem.NOT_DESTINATION -> StrBranchTransfers.pNotDestination
    BranchTransfer.Problem.NOT_SENDER -> StrBranchTransfers.pNotSender
    BranchTransfer.Problem.CLOSED -> StrBranchTransfers.pClosed
    BranchTransfer.Problem.PARTIAL_RECEIVED -> StrBranchTransfers.pPartialReceived
    BranchTransfer.Problem.BAD_STATE -> StrBranchTransfers.pBadState
  }
}

/** پالایه‌های فهرست (کامپیوتر و گوشی) */
enum class TransferFilter { INCOMING, OUTGOING, DRAFTS, CLOSED }

object BranchTransferPaths {
  const val LIST = "branches/transfers"
  const val NEW = "branches/transfers/new"
  fun detail(id: String) = "$LIST/" + ir.vil3ntec.tohid.core.nav.RoutePaths.encode(id)
  fun edit(id: String) = detail(id) + "/edit"
}

/** فهرست و پالایه‌ها */
@Stable
class BranchTransfersState(private val src: BranchTransfersSource, val here: String, private val onChanged: suspend (List<BranchTransfer.Transfer>) -> Unit = {}) {
  var list by mutableStateOf<List<BranchTransfer.Transfer>?>(null)
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  var filter by mutableStateOf(TransferFilter.INCOMING)

  suspend fun load() {
    busy = true
    failed = false
    when (val r = src.list()) {
      is ApiResult.Success -> { list = r.value; off = false; onChanged(r.value) }
      is ApiResult.Failure -> if (serverRefused(r.error)) { off = true; list = null } else failed = true
    }
    busy = false
  }

  fun of(f: TransferFilter): List<BranchTransfer.Transfer> = shown(list.orEmpty(), f)

  val shown: List<BranchTransfer.Transfer> get() = of(filter)

  /** در راه به این شعبه / از این شعبه / پیش‌نویس */
  val incomingOpen: Int get() = of(TransferFilter.INCOMING).size
  val outgoingOpen: Int get() = of(TransferFilter.OUTGOING).size
  val draftCount: Int get() = of(TransferFilter.DRAFTS).size

  companion object {
    fun shown(all: List<BranchTransfer.Transfer>, f: TransferFilter): List<BranchTransfer.Transfer> = all.filter { t ->
      when (f) {
        TransferFilter.INCOMING -> t.direction == Direction.IN && t.status.open
        TransferFilter.OUTGOING -> t.direction == Direction.OUT && t.status.open
        TransferFilter.DRAFTS -> t.status == Status.DRAFT
        TransferFilter.CLOSED -> !t.status.live
      }
    }

    fun label(f: TransferFilter): String = when (f) {
      TransferFilter.INCOMING -> StrBranchTransfers.incoming
      TransferFilter.OUTGOING -> StrBranchTransfers.outgoing
      TransferFilter.DRAFTS -> StrBranchTransfers.drafts
      TransferFilter.CLOSED -> StrBranchTransfers.closed
    }

    /** عنوانِ ردیف از نگاهِ همین شعبه: «به ب» یا «از الف» */
    fun title(t: BranchTransfer.Transfer): String =
      if (t.direction == Direction.OUT) StrBranchTransfers.toBranch(name(t.toBranch)) else StrBranchTransfers.fromBranch(name(t.fromBranch))

    fun name(e: BranchTransfer.End): String = ir.vil3ntec.tohid.core.i18n.branchName(e.name, e.main)
  }
}

/** یک انتقال: جزئیات و گام‌ها (دریافت، رد، ارسالِ پیش‌نویس، لغو) */
@Stable
class BranchTransferState(private val src: BranchTransfersSource, val here: String, val id: String, private val onChanged: suspend (List<BranchTransfer.Transfer>) -> Unit = {}) {
  var transfer by mutableStateOf<BranchTransfer.Transfer?>(null)
    private set
  var steps by mutableStateOf<List<BranchTransfer.Step>>(emptyList())
    private set
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var missing by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  /** پیامِ آخرین گام (خطا) */
  var message by mutableStateOf<String?>(null)
  /** کلیدِ گامِ بعدی: همان تا پاسخِ روشن (دو ضربه/قطعِ نت ⇒ همان گام)، سپس تازه */
  var stepKey by mutableStateOf(BranchTransfer.newKey())
    private set

  suspend fun load() {
    busy = true
    failed = false
    when (val r = src.detail(id)) {
      is ApiResult.Success -> { transfer = r.value.first; steps = r.value.second; missing = false; onChanged(listOf(r.value.first)) }
      is ApiResult.Failure -> when {
        r.error.code == "transfer_not_found" -> missing = true
        serverRefused(r.error) -> off = true
        else -> failed = true
      }
    }
    busy = false
  }

  val canReceive: Boolean get() = transfer?.let { BranchTransfer.canAct(it, here) } == true
  val canSend: Boolean get() = transfer?.let { BranchTransfer.canSend(it, here) } == true
  val canCancel: Boolean get() = transfer?.let { BranchTransfer.canCancel(it, here) } == true

  /** یک گامِ پیش‌سنجیده ⇒ انتقالِ تازه یا null (پیام در [message]) */
  suspend fun run(r: BranchTransfer.Result): BranchTransfer.Transfer? {
    if (busy) return null
    when (r) {
      is BranchTransfer.Result.Bad -> { message = BranchTransferText.problem(r.problem); return null }
      is BranchTransfer.Result.Ok -> {
        busy = true
        val out = src.step(r.call)
        busy = false
        return when (out) {
          is ApiResult.Success -> {
            transfer = out.value
            message = null
            stepKey = BranchTransfer.newKey()
            onChanged(listOf(out.value))
            //  گام‌های تازه از سرور (پاسخِ گام فقط انتقال را دارد)
            (src.detail(id) as? ApiResult.Success)?.let { steps = it.value.second }
            out.value
          }
          is ApiResult.Failure -> { message = StrBranchTransfers.failedWith(out.error.userMessage); null }
        }
      }
    }
  }

  suspend fun receive(counted: Map<String, Long>, final: Boolean, reason: String) =
    transfer?.let { run(BranchTransfer.receive(here, it, stepKey, counted.filterValues { v -> v > 0 }, final, reason)) }

  suspend fun reject(reason: String) = transfer?.let { run(BranchTransfer.reject(here, it, stepKey, reason)) }
  suspend fun sendDraft() = transfer?.let { run(BranchTransfer.sendDraft(here, it, stepKey)) }
  suspend fun cancel(reason: String) = transfer?.let { run(BranchTransfer.cancel(here, it, stepKey, reason)) }
}

/** فرمِ ارسال/پیش‌نویسِ تازه یا ویرایشِ پیش‌نویس ([editing]) */
@Stable
class BranchTransferFormState(private val src: BranchTransfersSource, val here: String, private val editing: String? = null, private val onChanged: suspend (List<BranchTransfer.Transfer>) -> Unit = {}) {
  var destinations by mutableStateOf<List<BranchTransfer.End>?>(null)
    private set
  var draft by mutableStateOf(BranchTransfer.Draft(BranchTransfer.newKey()))
  var off by mutableStateOf(false)
    private set
  var failed by mutableStateOf(false)
    private set
  var busy by mutableStateOf(false)
    private set
  var message by mutableStateOf<String?>(null)
  /** پاسخِ روشنِ آخرین ذخیره (صفحه می‌بندد) */
  var done by mutableStateOf<BranchTransfer.Transfer?>(null)
    private set

  val isEdit: Boolean get() = editing != null

  suspend fun load() {
    failed = false
    when (val r = src.destinations()) {
      is ApiResult.Success -> { destinations = r.value; if (draft.toBranch == null && r.value.size == 1) draft = draft.copy(toBranch = r.value.single().id) }
      is ApiResult.Failure -> if (serverRefused(r.error)) off = true else failed = true
    }
    val id = editing ?: return
    when (val r = src.detail(id)) {
      is ApiResult.Success -> draft = BranchTransfer.draftOf(r.value.first, draft.key)
      is ApiResult.Failure -> if (serverRefused(r.error)) off = true else failed = true
    }
  }

  /** [asDraft] ⇒ پیش‌نویسِ سرور (فقط فرمِ تازه)؛ ویرایش همیشه پیش‌نویس می‌ماند */
  suspend fun submit(asDraft: Boolean): BranchTransfer.Transfer? {
    if (busy || done != null) return null
    val r = editing?.let { id ->
      val head = BranchTransfer.Transfer(
        id = id, direction = Direction.OUT, fromBranch = BranchTransfer.End(here, "", here == BranchTransfer.MAIN),
        toBranch = BranchTransfer.End(draft.toBranch.orEmpty(), "", draft.toBranch == BranchTransfer.MAIN), from = draft.from, to = draft.to,
        status = Status.DRAFT, lines = emptyList(),
      )
      BranchTransfer.edit(here, head, draft)
    } ?: BranchTransfer.send(here, draft, draft = asDraft)
    when (r) {
      is BranchTransfer.Result.Bad -> { message = BranchTransferText.problem(r.problem); return null }
      is BranchTransfer.Result.Ok -> {
        busy = true
        val out = src.step(r.call)
        busy = false
        return when (out) {
          is ApiResult.Success -> { done = out.value; message = null; onChanged(listOf(out.value)); out.value }
          //  کلید همان می‌ماند: تلاشِ دوباره پس از قطعِ نت همان انتقال است (سرور `Idempotent-Replayed`)
          is ApiResult.Failure -> { message = StrBranchTransfers.failedWith(out.error.userMessage); null }
        }
      }
    }
  }
}
