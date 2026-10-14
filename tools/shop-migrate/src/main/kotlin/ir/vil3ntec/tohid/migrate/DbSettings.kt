package ir.vil3ntec.tohid.migrate

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/**
 *  تنظیمِ اتصالِ SQLite — یک جا، برای هر سکو.
 *
 *  ┌──────────────┬────────┬──────────────────────────────────────────────┐
 *  │ PRAGMA       │ مقدار  │ چرا                                          │
 *  ├──────────────┼────────┼──────────────────────────────────────────────┤
 *  │ journal_mode │ WAL    │ خواندن هم‌زمان با نوشتن، بی قفلِ کلِ فایل؛    │
 *  │              │        │ ماندگار در خودِ فایل (یک بار کافی است)       │
 *  │ synchronous  │ FULL   │ پس از هر COMMIT، WAL روی دیسک fsync می‌شود:   │
 *  │              │        │ قطعِ برق فروشِ تأییدشده را نمی‌برد. NORMAL   │
 *  │              │        │ (پیش‌فرضِ رایجِ WAL) آخرین تراکنش را ممکن     │
 *  │              │        │ است ببازد — برای پول پذیرفتنی نیست (۳.۲)      │
 *  │ foreign_keys │ ON     │ ارجاع‌ها واقعاً سنجیده شوند؛ پیش‌فرضِ SQLite  │
 *  │              │        │ خاموش است و برای هر اتصال جدا روشن می‌شود     │
 *  │ busy_timeout │ 5000   │ دو نویسنده به‌جای خطای فوری چند لحظه صبر کنند │
 *  └──────────────┴────────┴──────────────────────────────────────────────┘
 *
 *  synchronous و foreign_keys و busy_timeout مالِ **هر اتصال** است، نه فایل؛
 *  پس باید در بازکردنِ هر اتصال اعمال شوند:
 *   ‌• JVM/کامپیوتر: همین `jdbcProperties()` به `JdbcSqliteDriver` داده می‌شود
 *     و sqlite-jdbc آن‌ها را روی هر اتصالِ تازه اجرا می‌کند.
 *   ‌• اندروید (وقتی وصل شد): در `AndroidSqliteDriver(callback = …)`، داخلِ
 *     `onConfigure(db)`: `db.enableWriteAheadLogging()`،
 *     `db.setForeignKeyConstraintsEnabled(true)` و
 *     `db.execSQL("PRAGMA synchronous=FULL")`.
 *
 *  پایگاهِ درون‌حافظه (اجرای آزمایشی) WAL ندارد — `journal_mode` آن همیشه
 *  `memory` است؛ فقط foreign_keys روشن می‌شود.
 */
object DbSettings {

  val PRAGMAS: List<Pair<String, String>> = listOf(
    "journal_mode" to "WAL",
    "synchronous" to "FULL",
    "foreign_keys" to "ON",
    "busy_timeout" to "5000",
  )

  fun jdbcProperties(): Properties = Properties().apply {
    put("journal_mode", "WAL")
    put("synchronous", "FULL")
    put("foreign_keys", "true")
    put("busy_timeout", "5000")
  }

  /** پایگاهِ روی دیسک — فقط برای آزمون‌ها و مهاجرتِ واقعیِ قدم‌های بعد */
  fun openFile(file: File): JdbcSqliteDriver =
    JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", jdbcProperties())

  /**
   *  پایگاهِ درون‌حافظه — اجرای آزمایشی هیچ فایلی نمی‌سازد.
   *
   *  ⚠️ `JdbcSqliteDriver.IN_MEMORY` (`jdbc:sqlite:` با نامِ خالی) در SQLite
   *  یعنی «پایگاهِ موقتِ **روی دیسک**»، نه حافظه؛ پس صریحاً `:memory:`.
   */
  fun openInMemory(): JdbcSqliteDriver =
    JdbcSqliteDriver("jdbc:sqlite::memory:", Properties().apply { put("foreign_keys", "true") })

  fun pragma(driver: SqlDriver, name: String): String =
    driver.executeQuery(null, "PRAGMA $name", { c ->
      c.next()
      QueryResult.Value(c.getString(0) ?: c.getLong(0)?.toString() ?: "")
    }, 0).value
}
