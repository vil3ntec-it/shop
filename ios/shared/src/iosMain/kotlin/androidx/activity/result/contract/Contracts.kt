package androidx.activity.result.contract

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.PickVisualMediaRequest
import ir.vil3ntec.tohid.ios.IosPermissions
import ir.vil3ntec.tohid.ios.IosPickers

/*
 *  shimِ iOS ِ قراردادهای «انتخابِ فایل/عکس، دوربین، اجازه» ِ اندروید (همان نام‌های کامپیوتر) — هر کدام
 *  پنجرهٔ خودِ iOS را باز می‌کند (‎ir/…/ios/IosPickers.kt‎، ‎IosPermissions.kt‎) و پاسخ را با همان شکلِ اندروید
 *  روی رشتهٔ اصلی می‌دهد؛ لغو ⇐ همان «کاربر لغو کرد» (null، false، فهرستِ خالی).
 *  `StartActivityForResult` (برنامهٔ دیگرِ اندروید، مثلِ تشخیصِ گفتار) در iOS نیست ⇐ «لغو».
 */
abstract class ActivityResultContract<I, O> {
  internal abstract fun run(input: I, done: (O) -> Unit)
}

class ActivityResultContracts private constructor() {
  class RequestPermission : ActivityResultContract<String, Boolean>() {
    override fun run(input: String, done: (Boolean) -> Unit) = IosPermissions.request(input, done)
  }

  class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
    override fun run(input: Array<String>, done: (Map<String, Boolean>) -> Unit) = IosPermissions.requestAll(input, done)
  }

  class GetContent : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) = IosPickers.pickDocuments(input, multiple = false) { done(it.firstOrNull()) }
  }

  class GetMultipleContents : ActivityResultContract<String, List<Uri>>() {
    override fun run(input: String, done: (List<Uri>) -> Unit) = IosPickers.pickDocuments(input, multiple = true, done)
  }

  class CreateDocument(private val mime: String = "*/*") : ActivityResultContract<String, Uri?>() {
    override fun run(input: String, done: (Uri?) -> Unit) = done(IosPickers.createDocument(input))
  }

  class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun run(input: PickVisualMediaRequest, done: (Uri?) -> Unit) = IosPickers.pickImages(1) { done(it.firstOrNull()) }

    sealed interface VisualMediaType
    object ImageOnly : VisualMediaType
    object ImageAndVideo : VisualMediaType
    object VideoOnly : VisualMediaType
  }

  class TakePicture : ActivityResultContract<Uri, Boolean>() {
    override fun run(input: Uri, done: (Boolean) -> Unit) = IosPickers.takePicture(input, done)
  }

  class StartActivityForResult : ActivityResultContract<Intent, ActivityResult>() {
    override fun run(input: Intent, done: (ActivityResult) -> Unit) = done(ActivityResult(0, null))
  }
}
