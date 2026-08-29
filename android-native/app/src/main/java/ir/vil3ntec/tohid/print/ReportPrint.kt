package ir.vil3ntec.tohid.print

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import ir.vil3ntec.tohid.data.ReportCsv

/**
 *  «چاپ گزارش» — همان دکمه‌ای که نسخهٔ وب دارد.
 *
 *  اندروید خودش چاپگر و PDF را می‌شناسد؛ کافی است یک صفحهٔ HTML به
 *  `PrintManager` بدهیم تا کاربر بتواند روی کاغذ چاپ کند یا «ذخیره به
 *  صورت PDF» را بزند. همان جدولی چاپ می‌شود که خروجی CSV دارد، تا عدد
 *  کاغذ و عدد فایل هرگز با هم فرق نکنند.
 *
 *  `WebView` تا وقتی چاپ تمام نشده باید زنده بماند — برای همین مرجعش در
 *  یک متغیر نگه داشته می‌شود، وگرنه وسط کار جمع می‌شود و چاپ نصفه می‌ماند.
 */
object ReportPrint {

  private var holder: WebView? = null

  fun print(context: Context, sheet: ReportCsv.Sheet, title: String) {
    val web = WebView(context)
    web.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView, url: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        manager.print(
          sheet.name,
          view.createPrintDocumentAdapter(sheet.name),
          PrintAttributes.Builder().build(),
        )
        holder = null
      }
    }
    holder = web
    web.loadDataWithBaseURL(null, html(sheet, title), "text/html", "UTF-8", null)
  }

  private fun html(sheet: ReportCsv.Sheet, title: String): String {
    val head = sheet.rows.firstOrNull().orEmpty()
    val body = sheet.rows.drop(1)
    return buildString {
      append("<!doctype html><html dir=\"rtl\" lang=\"fa\"><head><meta charset=\"utf-8\">")
      append("<style>")
      append("body{font-family:sans-serif;padding:16px;color:#111}")
      append("h1{font-size:17px;margin:0 0 4px}")
      append("p{font-size:11px;color:#666;margin:0 0 14px}")
      append("table{width:100%;border-collapse:collapse;font-size:11px}")
      append("th,td{border:1px solid #ccc;padding:5px 6px;text-align:right}")
      append("th{background:#f2f2f2;font-weight:bold}")
      append("tr:nth-child(even) td{background:#fafafa}")
      append("</style></head><body>")
      append("<h1>").append(escape(title)).append("</h1>")
      append("<p>").append(escape(sheet.name)).append("</p>")
      if (body.isEmpty()) {
        append("<p>داده‌ای برای چاپ نیست.</p>")
      } else {
        append("<table><thead><tr>")
        head.forEach { append("<th>").append(escape(it)).append("</th>") }
        append("</tr></thead><tbody>")
        body.forEach { row ->
          append("<tr>")
          row.forEach { append("<td>").append(escape(it)).append("</td>") }
          append("</tr>")
        }
        append("</tbody></table>")
      }
      append("</body></html>")
    }
  }

  private fun escape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
}
