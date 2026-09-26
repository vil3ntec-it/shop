package androidx.activity.result.contract

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 *  قراردادهای «نتیجه از جای دیگر» روی کامپیوتر.
 *
 *  - اجازه‌ها همه داده شده‌اند (کامپیوتر اجازهٔ جدا برای دوربین و
 *    لوکیشن و اعلان ندارد).
 *  - انتخاب و ذخیرهٔ فایل با پنجرهٔ **خودِ سیستم** (`FileDialog`) — همان
 *    پنجره‌ای که کاربرِ ویندوز و مک می‌شناسد.
 */
abstract class ActivityResultContract<I, O> {
  internal abstract fun run(input: I, done: (O) -> Unit)
}

object DesktopFiles {
  /** پنجرهٔ اصلی — تا پنجرهٔ فایل رویش بنشیند؛ `Main.kt` پرش می‌کند */
  @Volatile var owner: Frame? = null

  fun open(title: String, filter: (String) -> Boolean = { true }): File? {
    val d = FileDialog(owner, title, FileDialog.LOAD)
    d.setFilenameFilter { _, name -> filter(name.lowercase()) }
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name)
  }

  fun save(title: String, suggested: String): File? {
    val d = FileDialog(owner, title, FileDialog.SAVE)
    d.file = suggested
    d.isVisible = true
    val name = d.file ?: return null
    return File(d.directory, name)
  }

  val IMAGE: (String) -> Boolean = { n -> listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { n.endsWith(it) } }
}

class ActivityResultContracts private constructor() {

  class RequestPermission : ActivityResultContract<String, Boolean>() {
    override fun run(input: String, done: (Boolean) -> Unit) = done(true)
  }

  class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
    override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) =
      done(input.associateWith { true })
  }

  class GetContent : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) {
      val filter: (String) -> Boolean = when {
        input.startsWith("image/") -> DesktopFiles.IMAGE
        else -> { _ -> true }
      }
      done(DesktopFiles.open("انتخاب فایل", filter)?.let(Uri::fromFile))
    }
  }

  class CreateDocument(private val mime: String = "*/*") : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) =
      done(DesktopFiles.save("ذخیره", input)?.let(Uri::fromFile))
  }

  class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun run(input: PickVisualMediaRequest, done: (Uri?) -> Unit) =
      done(DesktopFiles.open("انتخاب تصویر", DesktopFiles.IMAGE)?.let(Uri::fromFile))

    sealed interface VisualMediaType
    object ImageOnly : VisualMediaType
    object ImageAndVideo : VisualMediaType
    object VideoOnly : VisualMediaType
  }

  /**
   *  «عکس گرفتن» — کامپیوتر دوربینِ عکاسیِ برنامه ندارد؛ کاربر عکسی را
   *  که گرفته (با گوشی یا وب‌کم) از روی دیسک برمی‌دارد و همان در جای
   *  خواسته‌شده نوشته می‌شود.
   */
  class TakePicture : ActivityResultContract<Uri, Boolean>() {
    override fun run(input: Uri, done: (Boolean) -> Unit) {
      val src = DesktopFiles.open("انتخاب عکس", DesktopFiles.IMAGE) ?: return done(false)
      val target = input.path?.let(::File) ?: return done(false)
      val ok = runCatching { target.parentFile?.mkdirs(); src.copyTo(target, overwrite = true) }.isSuccess
      done(ok)
    }
  }

  class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>() {
    override fun run(input: Intent, done: (ActivityResult) -> Unit) =
      done(ActivityResult(Activity.RESULT_CANCELED, null))
  }
}
