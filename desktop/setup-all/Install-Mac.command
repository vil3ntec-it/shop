#!/bin/bash
# «فروشگاه من» — نصب روی مک: نوعِ تراشه (اپل یا اینتل) خودش تشخیص داده می‌شود
cd "$(dirname "$0")" || exit 1
if [ "$(uname -m)" = "arm64" ] || sysctl -n machdep.cpu.brand_string 2>/dev/null | grep -q "Apple"; then
  dmg="Mac-Apple-Silicon/MyShop-Mac-AppleSilicon.dmg"; echo "مکِ شما تراشهٔ اپل دارد."
else
  dmg="Mac-Intel/MyShop-Mac-Intel.dmg"; echo "مکِ شما اینتل است."
fi
xattr -dr com.apple.quarantine "$dmg" 2>/dev/null
open "$dmg"
echo "در پنجرهٔ باز شده، «فروشگاه من» را به پوشهٔ Applications بکشید."
