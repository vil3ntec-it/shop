package ir.vil3ntec.tohid.core.account

import ir.vil3ntec.tohid.core.account.AccountDeletion.Stage
import ir.vil3ntec.tohid.core.platform.StoreChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  «حذفِ حساب» ِ برنامه — مرحله‌ها و سنجشِ نشانی، همان قراردادِ `server/test/self-delete.test.js`.
 */
class AccountDeletionTest {

  @Test fun stage_follows_the_server_preview() {
    assertEquals(Stage.LOADING, AccountDeletion.stage(loaded = false, failed = false, enabled = false, blocked = null))
    assertEquals(Stage.OFFLINE, AccountDeletion.stage(loaded = false, failed = true, enabled = true, blocked = null))
    //  پرچمِ پیش‌فرض خاموش ⇒ راهِ پشتیبانی، نه دکمهٔ حذف
    assertEquals(Stage.SUPPORT, AccountDeletion.stage(loaded = true, failed = false, enabled = false, blocked = null))
    //  پمپ بر پرچم جلو می‌افتد: روشن یا خاموش، فقط پشتیبانی
    assertEquals(Stage.PUMP, AccountDeletion.stage(loaded = true, failed = false, enabled = true, blocked = "has_pump"))
    assertEquals(Stage.PUMP, AccountDeletion.stage(loaded = true, failed = false, enabled = false, blocked = "has_pump"))
    assertEquals(Stage.CONFIRM, AccountDeletion.stage(loaded = true, failed = false, enabled = true, blocked = null))
  }

  @Test fun email_must_match_ignoring_case_and_edges() {
    assertTrue(AccountDeletion.matches("  Ali@Shop.AF ", "email", "ali@shop.af"))
    assertFalse(AccountDeletion.matches("ali@shop.a", "email", "ali@shop.af"))
    assertFalse(AccountDeletion.matches("", "email", "ali@shop.af"))
    assertFalse(AccountDeletion.matches("x", "email", ""))
  }

  @Test fun phone_matches_in_any_digits_and_prefix() {
    assertTrue(AccountDeletion.matches("0700123456", "phone", "+93700123456"))
    assertTrue(AccountDeletion.matches("۰۷۰۰۱۲۳۴۵۶", "phone", "+93700123456"))
    assertTrue(AccountDeletion.matches("+93 700 123 456", "phone", "+93700123456"))
    assertFalse(AccountDeletion.matches("0700123457", "phone", "+93700123456"))
    assertFalse(AccountDeletion.matches("123", "phone", "+93700123456"))
  }

  @Test fun id_is_exact() {
    assertTrue(AccountDeletion.matches(" usr_ab12 ", "id", "usr_ab12"))
    assertFalse(AccountDeletion.matches("USR_AB12", "id", "usr_ab12"))
  }

  @Test fun bazaar_and_play_manage_updates_and_payments() {
    assertTrue(StoreChannel.storeManaged("bazaar"))
    assertTrue(StoreChannel.storeManaged("play"))
    assertFalse(StoreChannel.storeManaged("direct"))
    assertFalse(StoreChannel.storeManaged(""))
  }
}
