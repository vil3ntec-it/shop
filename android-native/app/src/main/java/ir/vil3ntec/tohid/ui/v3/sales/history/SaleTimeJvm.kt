package ir.vil3ntec.tohid.ui.v3.sales.history

/**
 *  درزِ پلتفرمِ [SaleTime] — تنها جایی از فروشِ نسخهٔ ۳ که `java.util` می‌خواند. با KMP همین
 *  `actual` ِ JVM می‌شود و آیفون فاصله را از `NSTimeZone` می‌گیرد.
 */
internal fun deviceZoneOffset(at: Long): Int = java.util.TimeZone.getDefault().getOffset(at)
