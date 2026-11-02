package android.speech

/** shimِ iOS — نام‌های «تشخیصِ گفتار» ِ اندروید؛ iOS هیچ برنامه‌ای برایش ندارد (‎PackageManager‎ ⇐ خالی، گامِ ۳: Speech.framework) */
object RecognizerIntent {
  const val ACTION_RECOGNIZE_SPEECH = "android.speech.action.RECOGNIZE_SPEECH"
  const val EXTRA_LANGUAGE_MODEL = "android.speech.extra.LANGUAGE_MODEL"
  const val LANGUAGE_MODEL_FREE_FORM = "free_form"
  const val LANGUAGE_MODEL_WEB_SEARCH = "web_search"
  const val EXTRA_LANGUAGE = "android.speech.extra.LANGUAGE"
  const val EXTRA_LANGUAGE_PREFERENCE = "android.speech.extra.LANGUAGE_PREFERENCE"
  const val EXTRA_PROMPT = "android.speech.extra.PROMPT"
  const val EXTRA_MAX_RESULTS = "android.speech.extra.MAX_RESULTS"
  const val EXTRA_RESULTS = "android.speech.extra.RESULTS"
  const val EXTRA_PARTIAL_RESULTS = "android.speech.extra.PARTIAL_RESULTS"
}
