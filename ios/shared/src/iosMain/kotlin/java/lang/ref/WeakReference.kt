package java.lang.ref

/** shimِ iOS ِ `WeakReference` روی `kotlin.native.ref.WeakReference` (همان معنا: نگه نمی‌دارد) */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
class WeakReference<T : Any>(referent: T?) {
  private val ref = referent?.let { kotlin.native.ref.WeakReference(it) }
  fun get(): T? = ref?.get()
  fun clear() = ref?.clear()
}
