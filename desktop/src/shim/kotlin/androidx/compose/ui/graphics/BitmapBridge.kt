package androidx.compose.ui.graphics

/** `Bitmap.asImageBitmap()`ِ اندروید، برای `Bitmap`ِ پوسته‌ایِ کامپیوتر */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = image.toComposeImageBitmap()
