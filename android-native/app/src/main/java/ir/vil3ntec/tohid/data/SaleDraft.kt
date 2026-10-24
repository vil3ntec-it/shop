package ir.vil3ntec.tohid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 *  پیش‌نویسِ فاکتور — POS-13 (بازیابی پس از بسته شدن/قطعِ برق) و POS-14
 *  (کلیدِ یکتای فاکتور).
 *
 *  ── کلیدِ فاکتور ──────────────────────────────────────────────────────
 *  هر پیش‌نویس از نخستین قلم یک `key` (ULID) دارد و فروشی که از آن ثبت
 *  می‌شود **همین شناسه** را می‌گیرد. پس:
 *   • دو ضربه روی «ثبتِ فروش» یک فروش می‌سازند — دومی با همان شناسه رد
 *     می‌شود (`SalesEngine.record(saleId = key)`).
 *   • اگر برق میانِ «دفتر نوشته شد» و «پیش‌نویس پاک شد» برود، پیش‌نویسی
 *     می‌ماند که کلیدش در دفتر هست ⇒ دوباره پیشنهاد **نمی‌شود** (`decide`).
 *  شکلِ دفتر عوض نمی‌شود: شناسهٔ فروش همان ULID است که بود.
 *
 *  ── شکلِ فایل ────────────────────────────────────────────────────────
 *  `{"v":2,"key":…,"lines":[…],"debtorId":…,"savedAt":…}`. شکلِ قدیمی
 *  (آرایهٔ ردیف‌ها، همان `tohid-shop-draft-cart-v1`ِ وب و برنامهٔ قبلی) هم
 *  خوانده می‌شود و کلید ندارد.
 */
@Serializable
data class SaleDraft(
  val v: Int = 2,
  val key: String? = null,
  val lines: List<Line> = emptyList(),
  val debtorId: String? = null,
  val savedAt: Long = 0,
) {
  /** همان شکلِ ردیفِ پیش‌نویسِ قدیمی؛ فیلدهای تازه پیش‌فرض دارند */
  @Serializable
  data class Line(
    val productId: String,
    val quantity: Double,
    val unitPrice: Double? = null,
    val label: String = "",
    /** سریِ اسکن‌شده (افزودنی) */
    val lotId: String? = null,
  )

  fun cart(): List<SalesEngine.CartLine> = lines.map { SalesEngine.CartLine(it.productId, it.quantity, it.unitPrice, it.label, it.lotId) }

  companion object {
    fun of(key: String?, cart: List<SalesEngine.CartLine>, debtorId: String?, now: Long) = SaleDraft(
      key = key,
      lines = cart.map { Line(it.productId, it.quantity, it.unitPrice, it.label, it.lotId) },
      debtorId = debtorId,
      savedAt = now,
    )
  }
}

object SaleDraftCodec {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

  fun encode(d: SaleDraft): String = json.encodeToString(d)

  /** v2 (شیء) یا v1 (آرایه). خالی یا خراب ⇒ `null` — هرگز خطا بالا نمی‌رود. */
  fun decode(raw: String?): SaleDraft? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
      when (val el = json.parseToJsonElement(raw)) {
        is JsonArray -> SaleDraft(lines = json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(SaleDraft.Line.serializer()), el))
        is JsonObject -> json.decodeFromJsonElement(SaleDraft.serializer(), el)
        else -> null
      }
    }.getOrNull()
  }
}

/** با پیش‌نویسی که از بارِ پیش مانده چه کنیم */
sealed interface DraftRestore {
  /** چیزی برای بازیابی نیست */
  data object None : DraftRestore
  /** همین پیش‌نویس پیش‌تر فروش شده (کلیدش در دفتر هست) ⇒ دور انداخته شود، نه دوباره */
  data class Posted(val key: String) : DraftRestore
  /** پیشنهادِ ادامه؛ `dropped` = ردیف‌هایی که کالایشان دیگر نیست یا غیرفعال است */
  data class Offer(val draft: SaleDraft, val dropped: Int) : DraftRestore
}

object SaleDraftRules {

  /** PRD-18 — فقط کالای موجود و فعال فروختنی است؛ سابقهٔ غیرفعال در گزارش‌ها می‌ماند */
  fun sellable(p: Product?): Boolean = p != null && p.active

  fun posted(d: ShopData, key: String?): Boolean = key != null && d.sales.any { it.id == key }

  fun decide(draft: SaleDraft?, d: ShopData): DraftRestore {
    if (draft == null || draft.lines.isEmpty()) return DraftRestore.None
    if (posted(d, draft.key)) return DraftRestore.Posted(draft.key!!)
    val byId = d.products.associateBy { it.id }
    //  قلمِ آزاد کالایی پشتش نیست و نباید قربانیِ این پالایش شود
    val kept = draft.lines.filter { it.label.isNotBlank() || sellable(byId[it.productId]) }
    if (kept.isEmpty()) return DraftRestore.None
    val debtor = draft.debtorId?.takeIf { id -> d.debtors.any { it.id == id } }
    return DraftRestore.Offer(draft.copy(lines = kept, debtorId = debtor), draft.lines.size - kept.size)
  }
}

/**
 *  POS-14 — «ثبتِ فروش» تا پایانِ ثبت قفل است و پیش‌نویس فقط وقتی پاک
 *  می‌شود که دفتر **به دیسک رسیده** باشد.
 *
 *  پیش‌تر سبد همان لحظه پاک می‌شد و نوشتنِ دفتر جدا در پس‌زمینه می‌رفت:
 *  اگر نوشتن شکست می‌خورد (قفلِ نرم، دیسکِ پر) یا برنامه همان لحظه می‌مُرد،
 *  هم فروش رفته بود هم سبد.
 */
class SalePost {
  private val gate = AtomicBoolean(false)
  private val _busy = MutableStateFlow(false)
  val busy: StateFlow<Boolean> = _busy.asStateFlow()

  sealed interface Outcome {
    /** ثبتِ دیگری در جریان است — ضربهٔ دوم نادیده گرفته شد */
    data object Busy : Outcome
    data class Refused(val message: String) : Outcome
    /** دفتر نوشته نشد ⇒ هیچ چیز ثبت نشده و سبد سرِ جایش است */
    data object NotSaved : Outcome
    data class Done(val ok: SalesEngine.Result.Ok) : Outcome
  }

  suspend fun run(
    key: String,
    record: (key: String) -> SalesEngine.Result,
    save: suspend (ShopData) -> Boolean,
    done: (key: String) -> Unit,
  ): Outcome {
    if (!gate.compareAndSet(false, true)) return Outcome.Busy
    _busy.value = true
    try {
      //  ساختنِ فروش (با سنجشِ دفترِ موجودی وقتی موتورِ تازه روشن است) بیرون از رشتهٔ صفحه —
      //  روی دفترِ بزرگ صندوق را قفل نمی‌کند. `record` تابعِ خالص است (`SalesEngine.record`).
      return when (val r = withContext(Dispatchers.Default) { record(key) }) {
        is SalesEngine.Result.Failed -> Outcome.Refused(r.message)
        //  رفتن از صفحه نباید نوشتن را نصفه بگذارد: یا هر دو، یا هیچ
        is SalesEngine.Result.Ok -> withContext(NonCancellable) {
          if (!save(r.data)) Outcome.NotSaved else { done(key); Outcome.Done(r) }
        }
      }
    } finally {
      gate.set(false)
      _busy.value = false
    }
  }

  companion object {
    const val NOT_SAVED = "فروش ثبت نشد — دفتر روی دستگاه نوشته نشد. سبد سرِ جایش است؛ دوباره بزنید."
  }
}
