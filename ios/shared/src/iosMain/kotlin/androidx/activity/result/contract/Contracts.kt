package androidx.activity.result.contract

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest

/*
 *  shimِ iOS ِ قراردادهای «انتخابِ فایل/عکس، دوربین، اجازه» ِ اندروید (همان نام‌های کامپیوتر).
 *  ⛔ گامِ ۱: هیچ‌کدام هنوز پنجرهٔ iOS باز نمی‌کند — همان پاسخِ «کاربر لغو کرد» (null، false،
 *  فهرستِ خالی) را می‌دهند، پس کدِ مشترک مسیرِ لغو را می‌رود و چیزی نمی‌نویسد. اجازه‌ها «نه».
 *  گامِ ۲: UIDocumentPicker، PHPicker، دوربین، و اجازهٔ iOS پشتِ همین نام‌ها.
 */
abstract class ActivityResultContract<I, O> {
  internal abstract fun run(input: I, done: (O) -> Unit)
}

class ActivityResultContracts private constructor() {
  class RequestPermission : ActivityResultContract<String, Boolean>() {
    override fun run(input: String, done: (Boolean) -> Unit) = done(false)
  }

  class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
    override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) = done(input.associateWith { false })
  }

  class GetContent : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) = done(null)
  }

  class GetMultipleContents : ActivityResultContract<String, List<Uri>>() {
    override fun run(input: String, done: (List<Uri>) -> Unit) = done(emptyList())
  }

  class CreateDocument(private val mime: String = "*/*") : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) = done(null)
  }

  class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun run(input: PickVisualMediaRequest, done: (Uri?) -> Unit) = done(null)

    sealed interface VisualMediaType
    object ImageOnly : VisualMediaType
    object ImageAndVideo : VisualMediaType
    object VideoOnly : VisualMediaType
  }

  class TakePicture : ActivityResultContract<Uri, Boolean>() {
    override fun run(input: Uri, done: (Boolean) -> Unit) = done(false)
  }

  class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>() {
    override fun run(input: Intent, done: (ActivityResult) -> Unit) = done(ActivityResult(0, null))
  }
}
