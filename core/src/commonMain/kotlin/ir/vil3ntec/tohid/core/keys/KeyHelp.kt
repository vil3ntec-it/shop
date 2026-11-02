package ir.vil3ntec.tohid.core.keys

/**
 *  راهنمای کلیدهای هر صفحه — S50، POS-41، MASTER-SPEC بخشِ ۳.۱۶.
 *
 *  «F1 در هر صفحه فهرستِ درستِ کلیدهای همان صفحه را نشان می‌دهد که از `keymap.json` آمده است» و
 *  «با نگه‌داشتنِ Ctrl نیم ثانیه، برچسب‌ها نمایان می‌شوند؛ Alt ِ تنها استفاده نشود».
 *
 *  خالص و بی‌خبر از `KeyMap` ِ برنامه: صدازننده هر کارِ keymap را یک [Item] می‌کند (برچسبِ کلید با Ctrl یا
 *  Cmd ِ همان پلتفرم)، و این‌جا فهرستِ همان صفحه ساخته می‌شود: کارهای خودِ صفحه، بعد سراسری‌هایی که آن
 *  صفحه نپوشانده؛ فقط آنچه در راهنما می‌آید (live و partial، نه planned).
 */
object KeyHelp {

  /** نگه‌داشتنِ Ctrl (یا Cmd) تا برچسب‌ها پیدا شوند */
  const val HOLD_MS: Long = 500

  /**
   *  @param keys برچسبِ هر کلید («Ctrl+B»)
   *  @param listed در راهنما می‌آید (live یا partial)
   *  @param overrides شناسهٔ کارهای سراسری‌ای که این کلیدِ صفحه عمداً می‌پوشاند
   *  @param mod نخستین کلیدش Ctrl/Cmd دارد — برچسبِ «نگه‌داشتنِ Ctrl»
   */
  data class Item(
    val id: String,
    val scope: String,
    val title: String,
    val keys: List<String>,
    val listed: Boolean,
    val overrides: List<String> = emptyList(),
    val mod: Boolean = false,
  )

  /** یک بخشِ پنجرهٔ F1: دامنه (صفحه یا سراسری) و کارهایش، به ترتیبِ keymap.json */
  data class Section(val scope: String, val items: List<Item>)

  /**
   *  مسیرِ صفحهٔ باز ⇒ دامنهٔ صفحه در keymap.json؛ `null` ⇒ صفحه کلیدِ خودش را ندارد (فقط سراسری).
   *  نخستین پارهٔ مسیر سنجیده می‌شود («sale/quick» همان «sale»).
   */
  fun scopeOf(route: String?): String? = when (route?.trim()?.substringBefore('/')?.substringBefore('?')) {
    "sale", "quick", "pos" -> "sale"
    "debtors", "debtor", "customers", "customer" -> "customers"
    "supplier", "suppliers" -> "supplier"
    "purchasing", "purchases", "purchase" -> "purchase"
    "orders" -> "orders"
    "stocktake", "count" -> "stocktake"
    "reports" -> "reports"
    "products", "product" -> "products"
    else -> null
  }

  /**
   *  فهرستِ F1 ِ یک صفحه: نخست کارهای خودِ صفحه ([screen])، سپس سراسری‌ها جز آن‌هایی که کارِ صفحه
   *  با `overrides` پوشانده. بخشِ خالی نمی‌آید. [globalScopes] = دامنه‌های سراسری (`kind == global`).
   */
  fun forScreen(items: List<Item>, globalScopes: Set<String>, screen: String?): List<Section> {
    val own = if (screen == null) emptyList() else items.filter { it.scope == screen && it.listed }
    val hidden = own.flatMap { it.overrides }.toSet()
    val global = items.filter { it.scope in globalScopes && it.listed && it.id !in hidden }
    return listOfNotNull(
      own.takeIf { it.isNotEmpty() }?.let { Section(screen!!, it) },
      global.takeIf { it.isNotEmpty() }?.let { Section(it.first().scope, it) },
    )
  }

  /** برچسب‌های «نگه‌داشتنِ Ctrl»: همان فهرستِ صفحه، فقط کلیدهای با Ctrl/Cmd */
  fun ctrlLabels(sections: List<Section>): List<Item> = sections.flatMap { it.items }.filter { it.mod }

  /**
   *  نگه‌داشتنِ Ctrl: فشردنِ خودِ Ctrl زمان را نگه می‌دارد، هر کلیدِ دیگر یا رها کردن آن را می‌اندازد؛
   *  برچسب‌ها از [HOLD_MS] پس از فشردن پیدا می‌شوند و با رها کردن می‌روند. Alt هرگز.
   */
  class Hold {
    private var since: Long? = null

    /** کلیدی پایین رفت: [ctrlAlone] = خودِ Ctrl یا Cmd، بی کلیدِ دیگر */
    fun down(ctrlAlone: Boolean, at: Long) {
      since = if (ctrlAlone) since ?: at else null
    }

    /** Ctrl رها شد */
    fun up() { since = null }

    fun shown(now: Long): Boolean = since?.let { now - it >= HOLD_MS } ?: false

    /** چند میلی‌ثانیهٔ دیگر برچسب‌ها پیدا می‌شوند؛ `null` ⇒ Ctrl پایین نیست */
    fun waitMs(now: Long): Long? = since?.let { maxOf(0L, HOLD_MS - (now - it)) }
  }
}
