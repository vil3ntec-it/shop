package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.purchase.InvoiceOcr

/** درهای «از عکسِ فاکتور» (PUR-12/AI-10) — جدا تا برگهٔ بازبینی بی سرور آزموده شود */
interface InvoiceOcrSource {
  /** پرچم، اجازه، اشتراک و نصبِ موتور (`GET /config`؛ خاموش ⇒ `enabled: false`) */
  suspend fun config(): ApiResult<InvoiceOcr.Config>

  /** بایت‌های JPEG ⇒ خط‌های متن (عکس و متن روی سرور نمی‌مانند) */
  suspend fun read(jpeg: ByteArray): ApiResult<List<InvoiceOcr.Line>>
}

/** `/api/shop/purchase-ocr` (`server/src/routes/purchase-ocr.js`): دیدن با `stock.view`، خواندن با `stock.adjust` */
class InvoiceOcrRepository(private val api: ApiClient) : InvoiceOcrSource {

  override suspend fun config(): ApiResult<InvoiceOcr.Config> = result { InvoiceOcr.config(api.get(InvoiceOcr.CONFIG)) }

  override suspend fun read(jpeg: ByteArray): ApiResult<List<InvoiceOcr.Line>> =
    result { InvoiceOcr.lines(api.postBytes(InvoiceOcr.ROOT, jpeg, "image/jpeg")) }
}
