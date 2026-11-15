package ir.vil3ntec.tohid.core.branch

import ir.vil3ntec.tohid.core.server.DataPartition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  شعبه درونِ یک دکان، فازِ ۲ (`docs/BRANCHES-fa.md`): کلیدِ دفتر = حساب@دکان#شعبه و سرآیندِ `X-Branch-Id` ِ Sync v3.
 *  ⛔ شعبهٔ اصلی (شناسهٔ خالی) هیچ چیزی را عوض نمی‌کند: همان کلیدِ دفتر، همان کلیدِ همگام‌سازی، بی سرآیند.
 *  همتای سرور: `server/src/lib/branches.js` (`idOf`) و `branch-key.js` (`<دکان>#<شعبه>`).
 */
class BranchKeyTest {

  private val user = "usr_0123456789abcdef01234567"
  private val shop = "shp_89abcdef0123456789abcdef"
  private val b1 = "brn_00112233445566778899aabb"
  private val b2 = "brn_00112233445566778899aabc"

  @Test
  fun mainBranchKeepsEverythingByteForByte() {
    val base = DataPartition.keyIn("", user, shop)
    assertEquals(base, BranchKey.vaultKey(base, BranchKey.MAIN))
    assertEquals(user, BranchKey.syncKey(user, BranchKey.MAIN))
    assertEquals("usr_x", BranchKey.syncFileKey("usr_x", BranchKey.MAIN))
    assertEquals(emptyMap(), BranchKey.headers(BranchKey.MAIN))
    assertEquals("$user@$shop", BranchKey.ledgerName(user, shop, BranchKey.MAIN))
    assertTrue(BranchKey.isMain(BranchKey.MAIN))
  }

  @Test
  fun ledgerNameIsAccountAtShopHashBranch() {
    assertEquals("$user@$shop#$b1", BranchKey.ledgerName(user, shop, b1))
  }

  @Test
  fun vaultKeyOfBranchSurvivesTheFortyEightCharFileName() {
    //  `ShopStore.safeKey` نامِ بایگانی را به ۴۸ نویسه می‌بُرد: «حساب@دکان» خودش ۵۷ نویسه است، پس «#شعبه» ِ ساده
    //  همان نامِ شعبهٔ اصلی می‌شد و دفترِ شعبه روی دفترِ اصلی می‌نشست
    val base = DataPartition.keyIn("", user, shop)
    val k1 = BranchKey.vaultKey(base, b1)
    val k2 = BranchKey.vaultKey(base, b2)
    assertTrue(k1.length <= 48 && k1.all { it.isLetterOrDigit() }, k1)
    assertNotEquals(base.take(48), k1)
    assertNotEquals(k1, k2)
    assertEquals(k1, BranchKey.vaultKey(base, b1))
    //  سرورِ دیگر (دامنهٔ دیگر) ⇒ نامِ دیگر
    assertNotEquals(k1, BranchKey.vaultKey(DataPartition.keyIn("s:other", user, shop), b1))
  }

  @Test
  fun syncStateOfEachBranchIsSeparate() {
    assertEquals("$user#$b1", BranchKey.syncKey(user, b1))
    assertNotEquals(BranchKey.syncKey(user, b1), BranchKey.syncKey(user, b2))
    //  کلیدِ پرونده: `OpQueue.fileKey` هرگز «-» ندارد ⇒ هیچ شعبه‌ای با حسابِ دیگری یکی نمی‌شود
    assertEquals("usr_x-$b1", BranchKey.syncFileKey("usr_x", b1))
    assertNotEquals(BranchKey.syncFileKey("usr_x", b1), BranchKey.syncFileKey("usr_x", b2))
  }

  @Test
  fun headerOnlyForNonMainBranchAndNeverSilentlyMain() {
    assertEquals(mapOf("X-Branch-Id" to b1), BranchKey.headers(b1))
    //  شناسهٔ بدشکل هرگز بی سرآیند (= اصلی) نمی‌رود: سرور خودش ۴۰۳ `branch_forbidden` می‌دهد
    assertEquals(mapOf("X-Branch-Id" to "brn_bad"), BranchKey.headers("brn_bad"))
  }

  @Test
  fun idOfMatchesTheServer() {
    assertEquals(BranchKey.MAIN, BranchKey.idOf(""))
    assertEquals(BranchKey.MAIN, BranchKey.idOf("main"))
    assertEquals(BranchKey.MAIN, BranchKey.idOf("  "))
    assertEquals(b1, BranchKey.idOf(" $b1 "))
    assertNull(BranchKey.idOf("brn_XYZ"))
    assertNull(BranchKey.idOf("shp_00112233445566778899aabb"))
    assertNull(BranchKey.idOf(null))
  }
}
