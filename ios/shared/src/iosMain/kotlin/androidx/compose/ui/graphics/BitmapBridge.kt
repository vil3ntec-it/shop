package androidx.compose.ui.graphics

/** `Bitmap.asImageBitmap()` ِ فقط-اندروید برای iOS: همان بیت‌مپِ Skia ِ shim (‎android/graphics/Bitmap.kt‎) */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = sk.asComposeImageBitmap()
