#!/bin/sh
# «فروشگاه من» — نصب روی لینوکسِ ۶۴ بیتی (دبیان، اوبونتو، مینت)
cd "$(dirname "$0")" || exit 1
case "$(uname -m)" in
  x86_64|amd64) ;;
  *) echo "این برنامه برای لینوکسِ ۶۴ بیتیِ اینتل/AMD است (سیستمِ شما: $(uname -m))."
     echo "نسخهٔ مرورگری: https://vil3ntec-it.github.io/shop/"; exit 1 ;;
esac
if command -v apt >/dev/null 2>&1; then
  sudo apt install -y "./Linux/MyShop-Linux.deb"
else
  sudo dpkg -i "./Linux/MyShop-Linux.deb"
fi
echo "نصب شد — «فروشگاه من» را از منوی برنامه‌ها باز کنید."
