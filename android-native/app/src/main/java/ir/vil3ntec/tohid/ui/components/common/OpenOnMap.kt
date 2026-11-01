package ir.vil3ntec.tohid.ui.components.common

import ir.vil3ntec.tohid.core.i18n.Str

//  این‌جا و نه در Welcome.kt: حالتِ پیک هم صدایش می‌زند و نباید به صفحهٔ دیگری وابسته باشد

/**
 *  باز کردنِ یک نقطه روی نقشه‌ی خودِ گوشی.
 *
 *  ── چرا نقشه‌ی گوشی و نه نقشه‌ی داخلِ برنامه ──────────────────────────
 *  نقشه‌ی توکار یعنی یک SDK، یک کلیدِ API، و چند مگابایت به حجمِ برنامه —
 *  برای کاری که هر گوشی‌ای خودش بلد است. `geo:` را هر برنامه‌ی نقشه‌ای
 *  می‌فهمد؛ اگر هیچ‌کدام نبود، نشانیِ وب باز می‌شود که مرورگر هم دارد.
 *  ──────────────────────────────────────────────────────────────────
 */
internal fun openOnMap(context: android.content.Context, lat: Double, lng: Double, label: String = Str.shop) {
  val point = String.format(java.util.Locale.US, "%f,%f", lat, lng)
  val geo = android.content.Intent(
    android.content.Intent.ACTION_VIEW,
    android.net.Uri.parse("geo:$point?q=$point(${android.net.Uri.encode(label)})"),
  )
  val opened = runCatching { context.startActivity(geo); true }.getOrDefault(false)
  if (opened) return
  runCatching {
    context.startActivity(
      android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$point"),
      )
    )
  }
}
