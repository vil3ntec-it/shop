package ir.vil3ntec.tohid.print

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.CutMode
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintBatch
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptRaster
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  چاپِ گزارشِ یک روز، ماه یا سالِ «تاریخچه» روی چاپگرِ فاکتور — از صفِ چاپ (`PrintSpooler`، نوعِ `REPORT`)، نه
 *  مستقیم: صفحه هرگز منتظرِ چاپگر نمی‌ماند و قطعِ بلوتوث همان تلاشِ دوبارهٔ صف را دارد.
 *
 *  سندها (`PeriodSlip`) یکی‌یکی در پس‌زمینه کشیده و سپرده می‌شوند؛ ترتیب و «لغو» در میانه با `PrintBatch` (هسته).
 *  تکه‌های میانی بی برش و بی سطرِ خالی‌اند تا نوار پیوسته بماند؛ فقط واپسین تکه برش می‌خورد. هر بار یک دسته.
 *  ⛔ اگر برنامه وسطِ کار بسته شود، تکه‌های سپرده‌شده در صف می‌مانند و چاپ می‌شوند (همان قاعدهٔ صف)؛ بقیه نه.
 */
object PeriodPrint {

  data class Progress(val title: String, val printed: Int, val total: Int, val phase: PrintBatch.Phase, val error: String? = null) {
    val running: Boolean get() = phase == PrintBatch.Phase.RUNNING
  }

  /** حالِ دستهٔ کنونی یا واپسین — صفحهٔ تاریخچه نوارِ پیشرفت و «لغو» را از همین می‌کشد */
  val progress = mutableStateOf<Progress?>(null)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val lock = Mutex()
  private var batch: PrintBatch? = null
  private var title = ""

  /** @return null یعنی آغاز شد؛ وگرنه پیامِ چرا نه */
  fun start(context: Context, printer: PrinterRegistry.Saved, title: String, docs: List<ReceiptDoc>): String? {
    if (progress.value?.running == true) return StrHistory.busy
    if (docs.isEmpty()) return null
    val b = PrintBatch(docs.size)
    batch = b
    this.title = title
    publish(b, null)
    val ctx = context.applicationContext ?: context
    scope.launch { drive(ctx, printer, docs, b) }
    return null
  }

  /** «لغوِ چاپ»: تکه‌های منتظر از صف کنار می‌روند؛ تکه‌ای که همین حالا چاپ می‌شود تمام می‌شود */
  fun cancel() {
    scope.launch {
      val b = batch ?: return@launch
      lock.withLock { b.cancel() }.forEach(PrintSpooler::discard)
      publish(b, null)
    }
  }

  /** بستنِ پیامِ پایان (چاپ شد / لغو شد) */
  fun dismiss() {
    if (progress.value?.running != true) progress.value = null
  }

  private suspend fun drive(ctx: Context, printer: PrinterRegistry.Saved, docs: List<ReceiptDoc>, b: PrintBatch) {
    val profile = printer.profile()
    val middle = profile.copy(cut = CutMode.NONE, feedLines = 0)
    val painter = runCatching { ReceiptPainter(ctx) }.getOrElse { stop(b, it.message); return }
    val waiting = ArrayDeque<Pair<String, CompletableDeferred<Boolean>>>()
    while (true) {
      val part = lock.withLock { b.take() }
      if (part != null) {
        val bytes = runCatching {
          EscPos.document(ReceiptRaster.compose(docs[part], painter), if (part == docs.lastIndex) profile else middle)
        }.getOrElse { stop(b, it.message); return }
        val done = CompletableDeferred<Boolean>()
        val label = if (docs.size > 1) "$title · ${part + 1}/${docs.size}" else title
        val sent = PrintSpooler.submit(ctx, PrintQueue.Kind.REPORT, printer.link, label, bytes, onDone = done)
        val id = sent.id ?: run { stop(b, sent.message); return }
        if (!lock.withLock { b.queued(id) }) { PrintSpooler.discard(id); break }
        waiting.addLast(id to done)
        publish(b, null)
        continue
      }
      val (id, done) = waiting.removeFirstOrNull() ?: break
      val printed = done.await()
      lock.withLock { b.finished(id, printed) }.forEach(PrintSpooler::discard)
      publish(b, null)
    }
    publish(b, null)
  }

  private suspend fun stop(b: PrintBatch, why: String?) {
    lock.withLock { b.fail() }.forEach(PrintSpooler::discard)
    publish(b, why ?: "?")
  }

  private fun publish(b: PrintBatch, error: String?) {
    progress.value = Progress(title, b.printed, b.total, b.phase, error ?: progress.value?.error?.takeIf { b.phase == PrintBatch.Phase.FAILED })
  }
}
