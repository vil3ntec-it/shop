package androidx.activity.result

import android.content.Intent

class ActivityResult(val resultCode: Int, val data: Intent?)

class PickVisualMediaRequest(val mediaType: Any? = null)
