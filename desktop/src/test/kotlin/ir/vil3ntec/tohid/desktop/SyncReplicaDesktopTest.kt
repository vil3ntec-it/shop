package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AccountBind
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  «شناسهٔ همگام‌سازیِ» هر حساب روی همین کامپیوتر (`V1ReplicaAndroid`، `core/sync/SyncReplica.kt`) روی دیسکِ واقعی و از
 *  همان درِ ورود (`AccountBind.signedIn`). سفرِ سرتاسری: `server/test/e2e/journeys-a.e2e.mjs` (J2b).
 */
class SyncReplicaDesktopTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val owner = "usr_rown$n"
  private val staff = "usr_rstf$n"
  private val shop = "shp_r$n"
  private val sync get() = SyncStore(ctx)
  private val engine get() = SyncV1Engine.of(ctx)
  private var ownerBefore = ""

  private fun clean() {
    sync.signOut()
    File(ctx.filesDir, "shop-data.json").delete()
    LedgerOwner.remember(ctx, "", "")
    SyncStateV1.setCurrentAccount(ctx, "")
    sync.accountName = ""
    ctx.filesDir.listFiles()?.filter { it.name.contains(n) }?.forEach { it.delete() }
    engine.rebind()
  }

  @Before fun before() { ownerBefore = sync.replicaOwner; sync.replicaOwner = ""; clean() }
  @After fun after() { clean(); sync.replicaOwner = ownerBefore }

  private fun signIn(store: ShopStore, user: String, name: String) {
    runBlocking { AccountBind.signedIn(ctx, store, user, shop, name) }
  }

  @Test fun `صاحب شناسهٔ خامِ دستگاه را نگه می‌دارد؛ شاگردِ تازه روی همان کامپیوتر شناسهٔ خودش را، پایدار`() {
    val store = ShopStore(ctx).also { runBlocking { it.load() } }
    val raw = sync.deviceUid
    signIn(store, owner, "صاحب")
    assertEquals(raw, engine.deviceId())
    //  صاحب از این دستگاه push کرده است
    SyncStateV1(ctx, engine.currentKey()).lastPushAt = 1L
    sync.signOut()

    signIn(store, staff, "علی")
    val mine = engine.deviceId()
    assertNotEquals(raw, mine)
    assertTrue(mine, mine.startsWith("$raw-") && mine.length <= 64)
    engine.rebind()
    assertEquals("پس از بستن و باز کردن همان", mine, engine.deviceId())
    sync.signOut()

    signIn(store, owner, "صاحب")
    assertEquals("صاحب که برگشت همان شناسهٔ خام", raw, engine.deviceId())
    sync.signOut()
    signIn(store, staff, "علی")
    assertEquals(mine, engine.deviceId())
  }

  @Test fun `حسابی که پیش از این نسخه با شناسهٔ خام push کرده عوض نمی‌شود`() {
    val store = ShopStore(ctx).also { runBlocking { it.load() } }
    val raw = sync.deviceUid
    sync.replicaOwner = owner
    signIn(store, staff, "علی")
    //  حالِ پیشینِ همین حساب: پیش‌تر از این دستگاه push شده، هنوز شناسه‌ای ثبت نشده
    val state = SyncStateV1(ctx, engine.currentKey())
    state.replica = ""
    state.lastPushAt = 5L
    assertEquals(raw, engine.deviceId())
  }
}
