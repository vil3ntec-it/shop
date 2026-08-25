package af.tohid.shop.data.repo

import af.tohid.shop.data.db.TohidDatabase

/** یک کالا که بیشتر از موجودی‌اش فروخته شده، به همراه اینکه چه کسی فروخته. */
data class Shortage(
    val productId: String,
    val productName: String,
    val unit: String,
    val stock: Double,          // منفی
    val shortage: Double,       // قدر مطلق
    val byOthers: List<Pair<String, Double>>,   // نام عضو → تعداد
)

/**
 * محاسبه‌ی موجودی و تشخیص کسری.
 *
 * موجودی = مجموع ورودی‌های انبار − فروش خالص (لغو‌نشده، منهای مرجوعی).
 * همان تعریفی که نسخه وب دارد، تا اعداد دو نسخه یکی باشد.
 */
class StockRepository(
    private val db: TohidDatabase,
    private val session: SessionStore,
) {

    suspend fun stockOf(productId: String): Double =
        db.warehouse().inboundFor(productId) - db.saleItems().soldQtyFor(productId)

    suspend fun stockStatus(productId: String, minStock: Double): String {
        val s = stockOf(productId)
        return when {
            s <= 0.0 -> "out"
            s <= minStock -> "low"
            else -> "ok"
        }
    }

    /** فهرست کالاهایی که موجودی‌شان منفی شده. */
    suspend fun shortages(memberNames: Map<String, String>): List<Shortage> {
        val me = session.userId()
        val out = mutableListOf<Shortage>()
        val items = db.saleItems().all()
        val sales = db.sales().allOnce().associateBy { it.id }

        for (p in db.products().all()) {
            val s = stockOf(p.id)
            if (s >= 0.0) continue

            val byOthers = HashMap<String, Double>()
            for (si in items) {
                if (si.productId != p.id) continue
                val sale = sales[si.saleId]
                if (sale != null && sale.status == "cancelled") continue
                val qty = si.quantity - si.returnedQty
                val owner = si.ownerUserId
                if (owner.isNotBlank() && owner != me) {
                    val name = memberNames[owner] ?: "یکی از اعضا"
                    byOthers[name] = (byOthers[name] ?: 0.0) + qty
                }
            }
            out += Shortage(
                productId = p.id, productName = p.name, unit = p.unit,
                stock = s, shortage = -s,
                byOthers = byOthers.entries.map { it.key to it.value },
            )
        }
        return out
    }

    /** پیام کوتاه و روشن — همان متنی که نسخه وب می‌دهد. */
    fun message(sh: Shortage): String {
        val u = if (sh.unit.isBlank()) "" else " ${sh.unit}"
        val who = sh.byOthers.joinToString("، ") { "${it.first} ${fa(it.second)}$u" }
        return if (who.isNotBlank()) {
            "«${sh.productName}»: ${fa(sh.shortage)}$u کسری. $who فروخته که تازه همگام شد. " +
                "اگر جنس در دکان هست، ورودی انبار ثبت کنید."
        } else {
            "«${sh.productName}»: ${fa(sh.shortage)}$u بیشتر از موجودی فروخته شده. " +
                "یعنی ورودی انبارش ثبت نشده. اگر جنس در دکان هست، ورودی انبار ثبت کنید."
        }
    }

    private fun fa(n: Double): String {
        val whole = if (n == Math.floor(n)) n.toLong().toString() else n.toString()
        val digits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')
        return buildString { for (c in whole) append(if (c in '0'..'9') digits[c - '0'] else c) }
    }
}
