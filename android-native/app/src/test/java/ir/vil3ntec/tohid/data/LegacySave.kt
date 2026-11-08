package ir.vil3ntec.tohid.data

/**
 *  ذخیرهٔ بی پایهٔ آزمون‌های پیش از پایهٔ صریح — همان راهِ پیشین (`ShopStore.saveGuessed`: پایه از روی شیءهای مشترک
 *  حدس زده می‌شود؛ همان خاموش‌کنِ اضطراریِ `shop.save_base`). آن آزمون‌ها همان رفتار را می‌سنجند و دست نخوردند.
 *  ⛔ کدِ برنامه این را ندارد (فقط مجموعهٔ آزمون) و ذخیرهٔ بی پایه در آن کامپایل نمی‌شود (`SaveBaseGuardTest`).
 */
internal suspend fun ShopStore.save(next: ShopData, replace: Boolean = false): Boolean = saveGuessed(next, replace)
