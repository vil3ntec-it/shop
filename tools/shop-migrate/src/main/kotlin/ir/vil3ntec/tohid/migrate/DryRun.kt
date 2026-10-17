package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.db.ShopDb

/**
 *  اجرای آزمایشی (قدمِ ۱ِ «مهاجرت داده»، بخشِ ۸): فقط گزارش.
 *
 *  ۱. محاسبهٔ قدیم روی دفتر (`LegacyFigures`) — و یک بارِ دیگر روی همان دفتر با
 *     ورودی‌های گردشده، تا اعشارِ Double از بقیهٔ اختلاف‌ها جدا شود.
 *  ۲. نگاشت به مدلِ تازه (`Mapper`) و نشاندن در SQLiteِ **درون‌حافظه** با همین
 *     طرح و همهٔ ماشه‌هایش (`Loader`).
 *  ۳. محاسبهٔ مدلِ تازه با SQL (`NewFigures`) و بازسنجیِ زنجیره‌های هش از روی
 *     خودِ پایگاه.
 *  ۴. مقایسه (`Reconcile`).
 *
 *  هیچ فایلی نوشته نمی‌شود: ورودی فقط خوانده می‌شود و پایگاه با پایانِ
 *  اجرا از حافظه می‌رود.
 */
object DryRun {

  data class ChainCheck(val saleLinks: Int, val auditLinks: Int, val broken: String?)

  class Result(
    val rows: List<Row>,
    val anomalies: List<Anomaly>,
    val info: Map<String, Long>,
    val chain: ChainCheck,
    val months: Int,
  ) {
    fun count(v: Verdict) = rows.count { it.verdict == v }
    val ok: Boolean get() = count(Verdict.UNEXPLAINED) == 0 && chain.broken == null
  }

  fun run(d: ShopData, storeName: String = ""): Result {
    val old = LegacyFigures.of(d)
    val rounded = LegacyFigures.of(LegacyFigures.roundInputs(d))
    val months = LegacyFigures.months(d)
    val model = Mapper.map(d, storeName)
    DbSettings.openInMemory().use { driver ->
      check(DbSettings.pragma(driver, "foreign_keys") == "1") { "foreign_keys is off" }
      ShopDb.Schema.create(driver)
      val db = ShopDb(driver)
      Loader.load(db, model)
      val new = NewFigures.of(db, model, months)
      return Result(
        rows = Reconcile.compare(old, rounded, new, model.anomalies),
        anomalies = model.anomalies,
        info = NewFigures.info(db, model),
        chain = verifyChains(db, model.device.id),
        months = months.size,
      )
    }
  }

  /** زنجیره‌ها از روی ردیف‌های خودِ پایگاه از نو حساب می‌شوند */
  fun verifyChains(db: ShopDb, deviceId: String): ChainCheck {
    val sales = db.salesQueries.saleChainOf(deviceId).executeAsList().map { s ->
      val lines = db.salesQueries.linesOfSale(s.id).executeAsList()
      val pays = db.salesQueries.paymentsOfSale(s.id).executeAsList()
      HashChain.Link(s.id, s.chainSeq ?: 0, s.prevHash ?: "", s.hash ?: "", Docs.sale(s, lines, pays))
    }
    val audit = db.auditQueries.auditChainOf(deviceId).executeAsList().map { a ->
      HashChain.Link(a.id, a.seq, a.prevHash, a.hash, Docs.audit(a))
    }
    val broken = HashChain.firstBroken(sales)?.let { "sale ${it.id}" }
      ?: HashChain.firstBroken(audit)?.let { "auditLog ${it.id}" }
    return ChainCheck(sales.size, audit.size, broken)
  }
}
