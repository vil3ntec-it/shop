package android.annotation

/** فقط نشانِ lint ِ اندروید؛ در iOS بی‌اثر */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD,
  AnnotationTarget.CONSTRUCTOR, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.EXPRESSION,
  AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class SuppressLint(vararg val value: String)

@Retention(AnnotationRetention.SOURCE)
annotation class TargetApi(val value: Int)
