package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestScope
import ir.vil3ntec.tohid.core.sync.v3.Hlc
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.FileV3Store
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.SyncV3Runner
import ir.vil3ntec.tohid.sync.v3.V3Entry
import ir.vil3ntec.tohid.sync.v3.V3Meta
import ir.vil3ntec.tohid.sync.v3.V3Queue
import ir.vil3ntec.tohid.sync.v3.V3StateDir
import ir.vil3ntec.tohid.sync.v3.v3Node
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  DATA-06 برای Sync v3 روی همان کدِ برنامه و دیسکِ واقعیِ کامپیوتر (`sync/v3/V3State.kt`، `V3StateDirAndroid.kt`):
 *  حالِ v3 (cursor، سایه، ساعت، بستگی، ژورنال، دفترچه) در `sync-v3/<کلید>/` و بیرون از پشتیبانِ اندروید؛ صف در
 *  `sync-v3-<کلید>/` و با پشتیبان (مثلِ `oplog-*` ِ v1)؛ حالِ ساختِ پیشین در نخستین خواندن کوچ می‌کند؛ حالِ دستگاهِ دیگر
 *  هنگامِ باز شدنِ دفتر حصار می‌خورد. موتور و سرور: `SyncV3EngineTest` (بخشِ DATA-06).
 */
class RestoredDeviceV3DesktopTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val account = "rdv3$n"
  private val key get() = OpQueue.fileKey(account)
  private val legacy get() = File(ctx.filesDir, "sync-v3-$key")
  private val stateDir get() = V3StateDir.of(ctx.filesDir, key)
  private val ledgerFile get() = File(ctx.filesDir, "shop-data.json")
  private val syncPrefs get() = ctx.getSharedPreferences("tohid-sync", 0)
  private var restoredBefore = 0L
  private val ledger = ShopData(products = listOf(Product(id = "rdv3-p1$n", name = "برنج", salePrice = 120.0)))
  private val oldEntry = V3Entry(
    key = "e000000000001", id = "op-old-$n", fmt = V3Entry.V3, table = "products", rowId = "rdv3-p1$n", at = 1L,
    ops = listOf(buildJsonObject { put("op_id", JsonPrimitive("op-old-$n")); put("table", JsonPrimitive("products")) }),
  )

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    //  شناسهٔ همین دستگاه پیش از هر چیز (ساختنش `restored_at` را می‌نویسد)؛ «بازیابی» این‌جا فقط از ساعتِ حال
    SyncStore(ctx).deviceUid
    restoredBefore = syncPrefs.getLong("restored_at", 0L)
    syncPrefs.edit().remove("restored_at").commit()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    SyncStateV1.setCurrentAccount(ctx, account)
    SyncV1Engine.of(ctx).rebind()
  }

  @After fun after() {
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    val e = syncPrefs.edit()
    if (restoredBefore > 0) e.putLong("restored_at", restoredBefore) else e.remove("restored_at")
    e.commit()
    SyncStateV1.setCurrentAccount(ctx, "")
    SyncV1Engine.of(ctx).rebind()
    legacy.deleteRecursively()
    stateDir.deleteRecursively()
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun meta(): V3Meta = V3StateDir.store(ctx.filesDir, key).loadMeta()!!
  private fun here(): String = v3Node(SyncStore(ctx).deviceUid)

  /** حالِ v3 در جای ساختِ پیشین (`sync-v3-<کلید>/`)، با ساعتی که [node] نوشت */
  private fun seedLegacy(node: String) {
    val s = FileV3Store(legacy)
    s.saveMeta(V3Meta(initialized = true, shadow = LedgerDiff.shadowOf(LedgerDiff.toJson(ledger)).toString(), cursor = 42, clock = Hlc.format(1_800_000_000_000L, 3L, node)))
    s.saveQueue(V3Queue(outbox = listOf(oldEntry), seq = 1))
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    AtRest.writeText(ctx, ledgerFile, Json.encodeToString(ShopData.serializer(), ledger))
  }

  @Test fun `حالِ v3 ِ دستگاهِ دیگر در جای ساختِ پیشین ⇒ کوچ به sync-v3، حصار هنگامِ باز شدن، صف همان‌جا و اول`() {
    seedLegacy("olddevice$n")
    val store = open()
    //  کوچ: حال بیرون از پشتیبان، صف سرِ جایش (با پشتیبان)
    assertFalse(File(legacy, V3StateDir.META).exists())
    assertTrue(File(stateDir, V3StateDir.META).isFile)
    assertTrue(File(legacy, V3StateDir.QUEUE).isFile)
    //  حصار: شناسه و گرهٔ همین دستگاه، cursor ِ کهنه دور، پایهٔ سایه = دفترِ روی میز، Pull بسته تا بازسازی
    val m = meta()
    assertEquals(here(), m.device)
    assertEquals(here(), Hlc.parse(m.clock)!!.node)
    assertEquals(0L, m.cursor)
    assertTrue(m.restorePending && m.restoredRebuild)
    assertEquals(LedgerDiff.shadowOf(LedgerDiff.toJson(store.data.value)).toString(), m.shadow)
    assertTrue(SyncV3Runner.of(ctx).restoredPending())
    //  نخستین فروشِ پس از بازیابی فقط خودش op می‌شود (کالای دفترِ پشتیبان «پایه» است، دوباره نمی‌رود)؛ صفِ پشتیبان می‌ماند
    val sale = Sale(id = "rdv3-s1$n", finalTotal = 120.0, createdAt = 1_800_000_000_000L, date = "2026-11-08")
    assertTrue(runBlocking { store.save(store.data.value, store.data.value.copy(sales = listOf(sale))) })
    val outbox = V3StateDir.store(ctx.filesDir, key).loadQueue()!!.outbox
    assertEquals(oldEntry.id, outbox.first().id)
    assertTrue(outbox.toString(), outbox.drop(1).any { it.table == "sales" && it.rowId == sale.id })
    assertTrue(outbox.toString(), outbox.drop(1).none { it.table == "products" })
    //  باز کردنِ دوباره ⇒ همان دستگاه، هیچ چرخشِ دیگری؛ هنوز «در انتظارِ بازسازی» (سایه فقط با همان فروش جلو رفت)
    open()
    val again = meta()
    assertEquals(m.device, again.device)
    assertEquals(0L, again.cursor)
    assertTrue(again.restorePending && again.restoredRebuild)
    assertEquals(oldEntry.id, V3StateDir.store(ctx.filesDir, key).loadQueue()!!.outbox.first().id)
  }

  @Test fun `به‌روزرسانیِ همین دستگاه ⇒ کوچ و فقط بستن، همان cursor، بی بازسازی`() {
    seedLegacy(here())
    open()
    val m = meta()
    assertEquals(here(), m.device)
    assertEquals(42L, m.cursor)
    assertFalse(m.restorePending || m.restoredRebuild)
    assertFalse(SyncV3Runner.of(ctx).restoredPending())
    assertEquals(listOf(oldEntry.id), V3StateDir.store(ctx.filesDir, key).loadQueue()!!.outbox.map { it.id })
  }

  @Test fun `کوچ فایل‌به‌فایل است، نیمه‌کاره ادامه می‌یابد و جای تازه هرگز رونویسی نمی‌شود`() {
    val root = File(ctx.filesDir, "rdv3-mig$n").apply { mkdirs() }
    try {
      val old = File(root, "old").apply { mkdirs() }
      val new = File(root, "new")
      for (f in listOf(V3StateDir.META, V3StateDir.JOURNAL, V3StateDir.REPLAY, V3StateDir.QUEUE)) File(old, f).writeText("old:$f")
      //  کوچِ نیمه‌کاره: state.json پیش‌تر رفته بود (برق میانِ دو rename)
      new.mkdirs()
      File(new, V3StateDir.META).writeText("new:state")
      File(old, V3StateDir.META).writeText("old:state-again")
      FileV3Store(new, old)
      assertEquals("new:state", File(new, V3StateDir.META).readText())
      assertEquals("جای پیشین پاک نمی‌شود", "old:state-again", File(old, V3StateDir.META).readText())
      assertEquals("old:${V3StateDir.JOURNAL}", File(new, V3StateDir.JOURNAL).readText())
      assertEquals("old:${V3StateDir.REPLAY}", File(new, V3StateDir.REPLAY).readText())
      assertFalse(File(old, V3StateDir.JOURNAL).exists() || File(old, V3StateDir.REPLAY).exists())
      //  صف کوچ نمی‌کند (با پشتیبان می‌رود)
      assertEquals("old:${V3StateDir.QUEUE}", File(old, V3StateDir.QUEUE).readText())
      assertFalse(File(new, V3StateDir.QUEUE).exists())
      //  بی جای پیشین ⇒ همان جای تازه، بی هیچ نوشتنی
      assertEquals(File(File(root, "none"), V3StateDir.META), V3StateDir.file(File(root, "none"), File(root, "nothing"), V3StateDir.META))
      assertFalse(File(root, "none").exists())
    } finally {
      root.deleteRecursively()
    }
  }

  @Test fun `پوشهٔ حالِ v3 در هیچ راهِ پشتیبانِ اندروید نیست و صفش هست`() {
    fun file(rel: String) = File("../android-native/app/src/main", rel).readText()
    fun dirs(xml: String) = Regex("""<exclude\s+domain="file"\s+path="([^"]+)"\s*/>""").findAll(xml).map { it.groupValues[1] }.toSet()
    val rules = file("res/xml/backup_rules.xml")
    val agentSrc = file("java/ir/vil3ntec/tohid/data/TohidBackupAgent.kt")
    val agent = Regex("\"([^\"]+)\"").findAll(Regex("""val EXCLUDED_DIRS = setOf\(([^)]*)\)""").find(agentSrc)!!.groupValues[1])
      .map { it.groupValues[1] }.toSet()
    val want = setOf("sync-shadow", V3StateDir.DIR)
    assertEquals("cloud-backup", want, dirs(rules.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")))
    assertEquals("device-transfer", want, dirs(rules.substringAfter("<device-transfer>").substringBefore("</device-transfer>")))
    assertEquals("backup_rules_legacy", want, dirs(file("res/xml/backup_rules_legacy.xml")))
    assertEquals("TohidBackupAgent", want, agent)
    //  رمزِ دفتر روشن: حال و صف هر دو بیرون («هر دو یا هیچ‌کدام»)
    for (n in listOf("sync-v3", "sync-v3/$key/state.json", "sync-v3/$key/journal.json", "sync-v3-$key/queue.json")) {
      assertTrue(n, AtRestScope.skipInDeviceBackup(n))
    }
  }
}
