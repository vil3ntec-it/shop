package android.app.backup

import android.content.ContextWrapper
import android.content.IosContext
import android.os.ParcelFileDescriptor
import java.io.File

/*
 *  shimِ iOS (گامِ ۶) — `data/TohidBackupAgent.kt` ِ اندروید (پشتیبانِ خودکارِ اندروید، SEC-01) روی آیفون کامپایل می‌شود
 *  ولی هرگز ساخته نمی‌شود: iOS عاملِ پشتیبان ندارد و پشتیبانِ iCloud را خودِ سیستم با قاعدهٔ پوشه‌ها می‌گیرد؛ کلیدهای
 *  دفترِ رمزدار و توکن در کلیددانِ «فقط همین دستگاه»اند و به پشتیبان نمی‌روند. فقط همان API، بی رفتار.
 */
abstract class BackupAgent : ContextWrapper(IosContext) {
  abstract fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?)
  abstract fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?)
  open fun onFullBackup(data: FullBackupDataOutput) {}
  fun fullBackupFile(file: File, output: FullBackupDataOutput) {}
}

class BackupDataInput internal constructor()
class BackupDataOutput internal constructor()
class FullBackupDataOutput internal constructor()
