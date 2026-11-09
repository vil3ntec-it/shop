package ir.vil3ntec.tohid.ios

import android.content.IosContext
import ir.vil3ntec.tohid.ios.backup.BackupExclusion
import ir.vil3ntec.tohid.ios.backup.IosNoBackupDirs
import ir.vil3ntec.tohid.ios.backup.NoBackupDirs
import java.io.File
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  پوشه‌های `sync-shadow/` و `sync-v3/` روی شبیه‌ساز (فقط CI ِ macOS؛ روی لینوکس فقط کامپایل): نخستین `filesDir` آن‌ها را با
 *  `NSURLIsExcludedFromBackupKey` ِ واقعی می‌سازد، ویژگیِ پاک‌شده (نصبِ پیشین) دوباره گذاشته می‌شود، و `filesDir`، دفتر و صفِ
 *  v3 (`sync-v3-<کلید>/`) با پشتیبان می‌مانند. فهرست و صدا زدنِ کمک برای هر پوشه روی لینوکس: ‎jvmTest/…/backup/NoBackupDirsJvmTest‎.
 */
class NoBackupDirsIosTest {

  @Test
  fun filesDirCreatesTheDirectoriesExcludedFromBackup() {
    val files = IosContext.filesDir
    assertTrue(IosNoBackupDirs.done)
    for (n in NoBackupDirs.NAMES) {
      val d = File(files, n)
      assertTrue(d.isDirectory, n)
      assertTrue(BackupExclusion.isExcluded(d), n)
    }
    //  خودِ `filesDir` (دفتر، `oplog-*`) در پشتیبان می‌ماند
    assertFalse(BackupExclusion.isExcluded(files))
  }

  @Test
  fun existingDirectoryWithoutTheAttributeGetsIt() {
    val d = File(IosContext.filesDir, NoBackupDirs.NAMES.last())
    try {
      assertTrue(NSURL.fileURLWithPath(d.path).setResourceValue(false, forKey = NSURLIsExcludedFromBackupKey, error = null))
      assertFalse(BackupExclusion.isExcluded(d))
      assertEquals(emptyList<String>(), NoBackupDirs.apply(IosNoBackupDirs.Disk(IosContext.filesDir)))
      assertTrue(BackupExclusion.isExcluded(d))
    } finally {
      BackupExclusion.exclude(d)
    }
  }

  @Test
  fun stateInsideIsCoveredButTheQueueBesideItIsNot() {
    val files = IosContext.filesDir
    val queue = File(files, "sync-v3-ios-test-nobackup")
    val state = File(File(files, "sync-v3"), "ios-test-nobackup")
    try {
      queue.mkdirs()
      state.mkdirs()
      //  ویژگی فقط روی خودِ پوشه است (درونش را پشتیبان با همان پوشه کنار می‌گذارد) و پوشهٔ کناری را نمی‌گیرد
      assertTrue(BackupExclusion.isExcluded(File(files, "sync-v3")))
      assertFalse(BackupExclusion.isExcluded(queue))
    } finally {
      state.deleteRecursively()
      queue.deleteRecursively()
    }
  }
}
