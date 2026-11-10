package ir.vil3ntec.tohid.sync.v3

/**
 *  SYN-04 روی iOS — همتای `V3GzipAndroid.kt` (که به `java.util.zip` تکیه دارد و در `ios-exclude.txt` است). iOS هنوز
 *  gzip ِ بومی ندارد، پس push ِ v3 همیشه ساده می‌رود: [MIN_CHARS] هرگز نمی‌رسد و `HttpV3Transport` به [pack] نمی‌رسد.
 *  سرور بدنهٔ ساده و فشرده را یکسان می‌پذیرد؛ فشرده‌سازی فقط صرفه‌جوییِ شبکه است، نه درستی.
 */
internal object V3Gzip {
  const val MIN_CHARS = Int.MAX_VALUE

  fun pack(text: String): ByteArray = error("V3Gzip.pack روی iOS به کار نمی‌رود (${text.length})")
}
