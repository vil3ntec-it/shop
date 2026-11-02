package android.os

import java.util.UUID

/** shimِ iOS — فقط نام (‎android/app/Service.kt‎) */
interface IBinder

/** shimِ iOS ِ `ParcelUuid` (سرویس‌های دستگاهِ بلوتوث؛ در iOS همیشه خالی) */
class ParcelUuid(val uuid: UUID) {
  override fun toString(): String = uuid.toString()
}
