package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.db.ShopDb
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** WAL و synchronous=FULL روی پایگاهِ روی دیسک واقعاً اعمال می‌شوند (فقط در پوشهٔ موقتِ آزمون) */
class PragmaTest {

  @Test fun fileDatabaseUsesWalAndFullSync() {
    val dir = Files.createTempDirectory("shop-migrate-pragma").toFile()
    try {
      DbSettings.openFile(dir.resolve("shop.db")).use { driver ->
        ShopDb.Schema.create(driver)
        assertEquals("wal", DbSettings.pragma(driver, "journal_mode").lowercase())
        assertEquals("2", DbSettings.pragma(driver, "synchronous"))   // 2 = FULL
        assertEquals("1", DbSettings.pragma(driver, "foreign_keys"))
        assertEquals("5000", DbSettings.pragma(driver, "busy_timeout"))
      }
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun inMemoryDatabaseStillEnforcesForeignKeys() {
    DbSettings.openInMemory().use { driver ->
      assertEquals("1", DbSettings.pragma(driver, "foreign_keys"))
      assertEquals("memory", DbSettings.pragma(driver, "journal_mode").lowercase())
    }
  }
}
