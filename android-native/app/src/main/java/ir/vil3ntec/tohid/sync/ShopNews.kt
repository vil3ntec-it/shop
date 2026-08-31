package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.EventsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 *  خبر دادن به بقیهٔ اعضای دکان.
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  زنگِ هشدارِ برنامه همه‌چیزش را از دفترِ **محلی** حساب می‌کرد. یعنی
 *  صاحب دکانی که خانه بود نمی‌دانست کریم چه فروخته یا چه کالایی تمام
 *  شده — تا وقتی خودش برنامه را باز کند و همگام‌سازی تمام شود.
 *
 *  حالا هر فروش و هر کالایی که تمام می‌شود، همان لحظه یک خبر روی سرورِ
 *  خودتان می‌گذارد و بقیه در زنگشان می‌بینندش.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ## چرا صف، و چرا شناسه
 *
 *  دکان جای بی‌آنتنی است. خبری که همان لحظه نرود نباید گم شود، پس روی
 *  گوشی صف می‌شود و دفعهٔ بعد می‌رود. و چون صف ممکن است دو بار فرستاده
 *  شود (پاسخ در راه گم شود)، هر خبر شناسهٔ خودش را دارد؛ سرور با همان
 *  شناسه ردیفِ تکراری نمی‌سازد و صاحب دکان یک فروش را دو بار نمی‌بیند.
 */
object ShopNews {

  private const val PREFS = "tohid-news"
  private const val QUEUE = "queue"
  private const val LAST_STOCK = "last_stock_out"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

  @kotlinx.serialization.Serializable
  private data class Queued(
    val kind: String,
    val title: String,
    val body: String,
    val clientId: String,
    val at: Long,
  )

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun read(context: Context): List<Queued> =
    runCatching {
      json.decodeFromString<List<Queued>>(prefs(context).getString(QUEUE, "[]") ?: "[]")
    }.getOrDefault(emptyList())

  private fun write(context: Context, items: List<Queued>) {
    //  صف سقف دارد: گوشی‌ای که یک ماه آفلاین بوده نباید هزار خبر
    //  انبار کند و بعد یک‌جا بفرستد
    val capped = items.takeLast(200)
    prefs(context).edit().putString(QUEUE, json.encodeToString(capped)).apply()
  }

  /** یک خبر تازه — می‌رود در صف و به‌زودی فرستاده می‌شود */
  fun post(context: Context, kind: String, title: String, body: String = "") {
    val app = context.applicationContext
    if (!Backend.isReady(app)) return          // بی‌حساب، خبری هم در کار نیست
    val item = Queued(
      kind = kind,
      title = title,
      body = body,
      clientId = newId(),
      at = System.currentTimeMillis(),
    )
    write(app, read(app) + item)
    flush(app)
  }

  /**
   *  فرستادنِ صف.
   *
   *  شکست بی‌صداست و صف دست‌نخورده می‌ماند: نبودنِ اینترنت خطا نیست و
   *  نباید جلوی کارِ فروشنده را بگیرد.
   */
  fun flush(context: Context) {
    val app = context.applicationContext
    if (!Backend.isReady(app) || !Backend.isOnline(app)) return
    val pending = read(app)
    if (pending.isEmpty()) return

    scope.launch {
      Backend.events(app)
        .send(pending.map {
          EventsRepository.Outgoing(it.kind, it.title, it.body, it.clientId, it.at)
        })
        .onSuccess {
          //  فقط همان‌هایی که فرستادیم برداشته می‌شوند؛ خبری که وسطِ
          //  کار اضافه شده باشد در صف می‌ماند
          val sentIds = pending.map { it.clientId }.toSet()
          write(app, read(app).filterNot { it.clientId in sentIds })
        }
    }
  }

  /**
   *  «این فروش انجام شد.»
   *
   *  فقط مبلغ و تعداد می‌رود، نه ریزِ فاکتور: خبر برای این است که صاحب
   *  دکان بداند چه گذشت، نه اینکه دفتر دو بار جابه‌جا شود.
   */
  fun sale(context: Context, total: Double, items: Int) {
    post(
      context,
      kind = "sale",
      title = "فروش تازه",
      body = "${ir.vil3ntec.tohid.money(total)} افغانی — ${items.fa()} قلم",
    )
  }

  /**
   *  «این کالا تمام شد.»
   *
   *  هر بار که دفتر عوض می‌شود سنجیده می‌شود، ولی فقط کالاهایی که
   *  **تازه** تمام شده‌اند خبر می‌دهند. بدون این، هر بار باز کردنِ
   *  برنامه همان فهرست را دوباره می‌فرستاد و زنگِ صاحب دکان پر از
   *  خبرِ تکراری می‌شد.
   */
  fun checkStock(context: Context, data: ShopData) {
    val app = context.applicationContext
    if (!Backend.isReady(app)) return

    val out = data.products
      .filter { ShopStore.stockStatus(data, it) == "out" }
      .map { it.id }
      .toSet()

    val known = prefs(app).getStringSet(LAST_STOCK, emptySet()).orEmpty()
    val fresh = out - known
    if (fresh.isNotEmpty()) {
      data.products.filter { it.id in fresh }.forEach { product ->
        post(app, kind = "stock_out", title = "${product.name} تمام شد", body = "موجودی صفر است")
      }
    }
    if (out != known) prefs(app).edit().putStringSet(LAST_STOCK, out).apply()
  }

  private fun newId(): String {
    val bytes = ByteArray(9)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun Int.fa(): String = toString()
}
