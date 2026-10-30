package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  فهرستِ یکتای هشدارهای دکان — جای سه فهرستِ پیشین (زنگِ سربرگِ قدیم، برگهٔ هشدارِ
 *  «حساب من» و پنجرهٔ «همه»ِ خانه). منطقِ خالص، بی Compose، با آزمونِ `ShopAlertsTest`.
 *
 *  منبع: هشدارهای خانه (`HomeAlerts.build`) + ردیف‌هایی که فقط زنگِ قدیم داشت، **همان
 *  قاعده‌ها، بی قاعدهٔ تازه**: کالای تمام‌شده و کم (نام‌ها زیرِ هشدارِ «کالا»)، بدهیِ پرداخت‌نشده به
 *  تأمین‌کننده‌ها، یادآوریِ پشتیبان، و سه قرض‌دارِ بیشترین قرض. اشتراک یک بار (از خانه)
 *  می‌آید. هر ردیف مستقیم سرِ کادرِ خودش می‌رود: کالا ⇐ همان کالا، قرض‌دار ⇐ همان
 *  قرض‌دار، تأمین‌کنندهٔ تنها ⇐ صفحهٔ خودش، پشتیبان ⇐ صفحهٔ پشتیبان.
 */
object ShopAlerts {
  enum class Kind { ORDERS, SUBSCRIPTION, SYNC, STOCK, DEBTS, EXPIRY, PRODUCT_OUT, PRODUCT_LOW, SUPPLIER, BACKUP, DEBTOR }

  data class Item(
    /** کلیدِ پایدار برای فهرست و حرکتِ ورود */
    val key: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    /** نوشتهٔ کارِ پایانِ ردیف («دیدن»، «تمدید»، …) */
    val action: String,
    val route: String,
    /** مقصدِ دکمهٔ کار، اگر جدا از ردیف است (اشتراک ⇐ پلن‌ها) */
    val actionRoute: String,
    val tone: V3Tone,
    val urgent: Boolean,
  ) {
    /** کارِ ردیف همان «دیدن» ِ خودِ ردیف است ⇐ دکمهٔ جدا لازم نیست */
    val plainSee: Boolean get() = action == StrHome.actionSee && actionRoute == route
  }

  data class Product(val id: String, val name: String)
  data class Low(val id: String, val name: String, val left: String)
  data class Owing(val id: String, val name: String, val amount: String)

  /** آنچه فقط زنگِ قدیم نشان می‌داد — از دفتر، بی هیچ شمارشِ تازه */
  data class Extras(
    val out: List<Product> = emptyList(),
    val low: List<Low> = emptyList(),
    /** جمعِ بدهی به تأمین‌کننده‌ها (متنِ پول) یا `null` وقتی صفر است */
    val supplierDebt: String? = null,
    /** تأمین‌کنندهٔ تنهایی که بدهی دارد ⇐ مستقیم صفحهٔ خودش؛ چند تا ⇐ فهرستِ خرید */
    val onlySupplierId: String? = null,
    val backupStale: Boolean = false,
    val backupText: String = "",
    /** سه قرض‌دارِ بیشترین قرض (همان قاعدهٔ زنگِ قدیم) */
    val topDebtors: List<Owing> = emptyList(),
  )

  data class Groups(val urgent: List<Item>, val rest: List<Item>) {
    val all: List<Item> get() = urgent + rest
    val isEmpty: Boolean get() = urgent.isEmpty() && rest.isEmpty()
  }

  fun fromHome(a: HomeAlerts.Alert): Item = Item(
    key = "home:" + a.kind.name + ":" + a.route,
    kind = when (a.kind) {
      HomeAlerts.Kind.ORDERS -> Kind.ORDERS
      HomeAlerts.Kind.SUBSCRIPTION -> Kind.SUBSCRIPTION
      HomeAlerts.Kind.SYNC -> Kind.SYNC
      HomeAlerts.Kind.STOCK -> Kind.STOCK
      HomeAlerts.Kind.DEBTS -> Kind.DEBTS
      HomeAlerts.Kind.EXPIRY -> Kind.EXPIRY
    },
    title = a.title,
    subtitle = a.body,
    action = a.action,
    route = a.route,
    actionRoute = a.actionRoute,
    tone = toneOf(a),
    urgent = a.urgent,
  )

  /**
   *  رنگِ ردیف (§۹) — سرخ فقط برای مشکلِ واقعی (فوری‌ها و کالای تمام‌شده)؛ سفارشِ تازه
   *  آبیِ «اطلاع»، کم‌بودن و عقب‌افتادن زردِ «هشدار».
   */
  private fun toneOf(a: HomeAlerts.Alert): V3Tone = when {
    a.kind == HomeAlerts.Kind.ORDERS -> V3Tone.INFO
    a.urgent -> V3Tone.ERROR
    a.kind == HomeAlerts.Kind.SYNC -> V3Tone.INFO
    else -> V3Tone.WARNING
  }

  /** «الف، ب، ج و ۲ دیگر» — چند نام در یک خط */
  fun names(all: List<String>, max: Int = 3): String {
    val shown = all.take(max).joinToString(StrHome.listSep)
    val more = all.size - max
    return if (more > 0) shown + " " + StrHome.andMore(more) else shown
  }

  /**
   *  ساده مثلِ خانه (HOME-V2): کالاهای تمام‌شده/کم وقتی هشدارِ جمعِ «کالا» هست، ردیفِ جدا
   *  نمی‌گیرند — نامشان زیرنویسِ همان یک ردیف می‌شود (تکرار نه). چند قرض‌دارِ بیشترین قرض هم
   *  یک ردیف ⇐ فهرستِ قرض‌دارها به ترتیبِ بیشترین؛ یکی ⇐ همان قرض‌دار.
   */
  fun build(home: List<HomeAlerts.Alert>, x: Extras): Groups {
    val products = x.out.map { it.name } + x.low.map { it.name }
    val folded = products.isNotEmpty() && home.any { it.kind == HomeAlerts.Kind.STOCK }
    val fromHome = home.map(::fromHome).map { if (folded && it.kind == Kind.STOCK) it.copy(subtitle = names(products)) else it }
    val extra = buildList {
      if (!folded) {
        x.out.forEach {
          add(Item("out:${it.id}", Kind.PRODUCT_OUT, it.name, StrHome.itemOut, StrHome.actionSee, ShellTargets.product(it.id), ShellTargets.product(it.id), V3Tone.ERROR, false))
        }
        x.low.forEach {
          add(Item("low:${it.id}", Kind.PRODUCT_LOW, it.name, StrHome.itemLeft(it.left), StrHome.actionSee, ShellTargets.product(it.id), ShellTargets.product(it.id), V3Tone.WARNING, false))
        }
      }
      x.supplierDebt?.let { amount ->
        val route = x.onlySupplierId?.let(ShellTargets::supplier) ?: ShellTargets.PURCHASING
        add(Item("supplier", Kind.SUPPLIER, StrHome.supplierDebt, "$amount ؋ · ${StrHome.unpaid}", StrHome.actionSee, route, route, V3Tone.WARNING, false))
      }
      if (x.backupStale) {
        add(Item("backup", Kind.BACKUP, StrHome.backupNow, x.backupText, StrHome.actionCheck, ShellTargets.BACKUP, ShellTargets.BACKUP, V3Tone.WARNING, false))
      }
      val top = x.topDebtors
      if (top.size == 1) top.single().let {
        add(Item("debtor:${it.id}", Kind.DEBTOR, it.name, StrHome.debtOf(it.amount), StrHome.actionFollowUp, ShellTargets.debtor(it.id), ShellTargets.debtor(it.id), V3Tone.INFO, false))
      } else if (top.size > 1) {
        val sub = names(top.map { it.name + " " + it.amount + " ؋" })
        add(Item("debtors:top", Kind.DEBTOR, StrHome.topDebts, sub, StrHome.actionFollowUp, ShellTargets.MOST_DEBT, ShellTargets.MOST_DEBT, V3Tone.INFO, false))
      }
    }
    return Groups(urgent = fromHome.filter { it.urgent }, rest = fromHome.filterNot { it.urgent } + extra)
  }

  /** نقطهٔ زنگ: هر هشدارِ فوری، یا خبرِ دیده‌نشده */
  fun bellDot(groups: Groups, unseenNews: Boolean): Boolean = groups.urgent.isNotEmpty() || unseenNews
}
