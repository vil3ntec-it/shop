package ir.vil3ntec.tohid.core.perm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** STF-05 — قفلِ خودکار پس از بی‌کاری؛ پیش‌فرض خاموش = رفتارِ پیشین */
class IdleLockTest {

  private val min = 60_000L

  @Test fun offByDefaultIsTodaysBehaviour() {
    //  ۰ دقیقه: هرگز، حتی پس از یک روز
    assertFalse(IdleLock.shouldLock(true, 0, 0, 24 * 60 * min))
    //  بی رمز: هرگز، هر چه انتخاب شده باشد
    assertFalse(IdleLock.shouldLock(false, 5, 0, 24 * 60 * min))
  }

  @Test fun locksAtTheBoundary() {
    assertFalse(IdleLock.shouldLock(true, 5, 1_000, 1_000 + 5 * min - 1))
    assertTrue(IdleLock.shouldLock(true, 5, 1_000, 1_000 + 5 * min))
    assertTrue(IdleLock.shouldLock(true, 1, 0, 2 * min))
  }

  @Test fun unknownStoredValueMeansOff() {
    assertEquals(0, IdleLock.clamp(7))
    assertEquals(0, IdleLock.clamp(-1))
    assertEquals(10, IdleLock.clamp(10))
    assertFalse(IdleLock.shouldLock(true, 7, 0, 999 * min))
  }

  @Test fun clockGoingBackwardsNeverLocks() {
    assertFalse(IdleLock.shouldLock(true, 1, 10 * min, 0))
  }
}
