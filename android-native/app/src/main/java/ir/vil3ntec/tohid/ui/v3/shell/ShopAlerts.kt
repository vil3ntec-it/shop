package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  فهرستِ یکتای هشدارهای دکان — جای سه فهرستِ پیشین (زنگِ سربرگِ قدیم، برگهٔ هشدارِ
 *  «حساب من» و پنجرهٔ «همه»ِ خانه). منطقِ خالص، بی Compose، با آزمونِ `ShopAlertsTest`.
 *
 *  منبع: هشدارهای خانه (`HomeAlerts.build`) + ردیف‌هایی که فقط زنگِ قدیم داشت، **همان
 *  قاعده‌ها، بی قاعدهٔ تازه**: هر کالای تمام‌شده و هر کالای کم، بدهیِ پرداخت‌نشده به
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
  )

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

  fun build(home: List<HomeAlerts.Alert>, x: Extras): Groups {
    val fromHome = home.map(::fromHome)
    val extra = buildList {
      x.out.forEach {
        add(Item("out:${it.id}", Kind.PRODUCT_OUT, it.name, StrHome.itemOut, StrHome.actionSee, ShellTargets.product(it.id), ShellTargets.product(it.id), V3Tone.ERROR, false))
      }
      x.low.forEach {
        add(Item("low:${it.id}", Kind.PRODUCT_LOW, it.name, StrHome.itemLeft(it.left), StrHome.actionSee, ShellTargets.product(it.id), ShellTargets.product(it.id), V3Tone.WARNING, false))
      }
      x.supplierDebt?.let { amount ->
        val route = x.onlySupplierId?.let(ShellTargets::supplier) ?: ShellTargets.PURCHASING
        add(Item("supplier", Kind.SUPPLIER, StrHome.supplierDebt, "$amount ؋ · ${StrHome.unpaid}", StrHome.actionSee, route, route, V3Tone.WARNING, false))
      }
      if (x.backupStale) {
        add(Item("backup", Kind.BACKUP, StrHome.backupNow, x.backupText, StrHome.actionCheck, ShellTargets.BACKUP, ShellTargets.BACKUP, V3Tone.WARNING, false))
      }
      x.topDebtors.forEach {
        add(Item("debtor:${it.id}", Kind.DEBTOR, it.name, StrHome.debtOf(it.amount), StrHome.actionFollowUp, ShellTargets.debtor(it.id), ShellTargets.debtor(it.id), V3Tone.INFO, false))
      }
    }
    return Groups(urgent = fromHome.filter { it.urgent }, rest = fromHome.filterNot { it.urgent } + extra)
  }

  /** نقطهٔ زنگ: هر هشدارِ فوری، یا خبرِ دیده‌نشده */
  fun bellDot(groups: Groups, unseenNews: Boolean): Boolean = groups.urgent.isNotEmpty() || unseenNews
}
