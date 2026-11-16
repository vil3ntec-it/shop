<#
  F0-09/SEC-08 — چرخشِ کلیدِ امضای برنامهٔ اندروید روی کامپیوترِ ویندوزیِ خودِ صاحبِ مخزن (همان کارِ rotate-signing-key.sh).
  راهنمای گام‌به‌گام: docs/OWNER-ACTIONS-fa.md («چرخشِ کلیدِ امضای اندروید»).

    powershell -ExecutionPolicy Bypass -File android-native\tools\rotate-signing-key.ps1 [-OldKeystore <مسیر>] [-Out <پوشهٔ تازه>]

  ۱) کلیدِ تازه (PKCS12، RSA 4096، ۳۰ سال) در پوشهٔ تازه؛ ۲) دودمانِ v3 با apksigner rotate (کلیدِ پیشین ⇒ تازه)؛
  ۳) نام و مقدارِ دقیقِ هر secret و متغیرِ GitHub (و همان‌ها در فایل‌های پوشه).
  ⛔ پوشهٔ خروجی هرگز در مخزن یا کامیت نه؛ یک رونوشتِ آفلاین (USB) نگه دارید.
#>
[CmdletBinding()]
param(
  [string]$OldKeystore = '',
  [string]$Out = ''
)
$ErrorActionPreference = 'Stop'
function Die([string]$m) { Write-Host "❌ $m" -ForegroundColor Red; exit 1 }
#  ابزارِ بیرونی (keytool/apksigner) گاهی روی stderr می‌نویسد؛ ویندوز پاورشل ۵ آن را با «Stop» خطای کشنده می‌کند.
#  نتیجه را خودمان با کدِ خروج و بودنِ فایل می‌سنجیم.
function Invoke-Native([scriptblock]$Block) {
  $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
  try { & $Block } finally { $ErrorActionPreference = $prev }
}

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleFile = Join-Path $here '..\app\build.gradle.kts'
$repoRoot = (Resolve-Path (Join-Path $here '..\..')).Path
if (-not $OldKeystore) { $OldKeystore = Join-Path $here '..\app\tohid-release.jks' }
if (-not $Out) { $Out = Join-Path ([Environment]::GetFolderPath('MyDocuments')) ("tohid-signing-rotation-" + (Get-Date -Format 'yyyyMMdd-HHmmss')) }

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool -and $env:JAVA_HOME) { $c = Join-Path $env:JAVA_HOME 'bin\keytool.exe'; if (Test-Path $c) { $keytool = $c } }
if (-not $keytool) { Die 'keytool پیدا نشد — JDK 17 را نصب کنید (Android Studio هم دارد) یا JAVA_HOME را بگذارید.' }

$apksigner = $env:APKSIGNER
if (-not $apksigner) {
  foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
    if ($sdk -and (Test-Path (Join-Path $sdk 'build-tools'))) {
      $bt = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } | Select-Object -Last 1
      foreach ($n in 'apksigner.bat', 'apksigner') { $c = Join-Path $bt.FullName $n; if (Test-Path $c) { $apksigner = $c; break } }
      if ($apksigner) { break }
    }
  }
}
if (-not $apksigner) { $apksigner = (Get-Command apksigner -ErrorAction SilentlyContinue).Source }
if (-not $apksigner) { Die 'apksigner پیدا نشد — Android SDK build-tools (۲۸ یا تازه‌تر) را نصب کنید یا $env:APKSIGNER را بدهید.' }
if (-not (Test-Path $OldKeystore)) { Die "کلیدِ پیشین پیدا نشد: $OldKeystore" }
if (Test-Path $Out) { Die "پوشهٔ خروجی از پیش هست: $Out — نامِ دیگری بدهید (-Out)." }
$outFull = [System.IO.Path]::GetFullPath($Out)
if ($outFull.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) { Die 'پوشهٔ خروجی درونِ مخزن است — جای دیگری بدهید.' }
New-Item -ItemType Directory -Path $outFull | Out-Null
#  فقط کاربرِ همین ویندوز (بی ارث از پوشهٔ بالا)
try {
  $acl = Get-Acl $outFull
  $acl.SetAccessRuleProtection($true, $false)
  $me = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
  $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($me, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
  Set-Acl $outFull $acl
} catch { Write-Host '⚠️ دسترسیِ پوشه محدود نشد؛ پوشه را جای امنی بگذارید.' }

function GradleDefault([string]$name) {
  $m = Select-String -Path $gradleFile -Pattern ('env\("' + $name + '"\) \?: "([^"]*)"') | Select-Object -First 1
  if ($m) { return $m.Matches[0].Groups[1].Value } else { return '' }
}
#  کلیدِ پیشین: همان قاعدهٔ Gradle — مقدارِ محیط، وگرنه پیش‌فرضِ build.gradle.kts
$env:OLD_PASS = if ($env:TOHID_KEYSTORE_PASSWORD) { $env:TOHID_KEYSTORE_PASSWORD } else { GradleDefault 'TOHID_KEYSTORE_PASSWORD' }
$env:OLD_KEY_PASS = if ($env:TOHID_KEY_PASSWORD) { $env:TOHID_KEY_PASSWORD } else { $env:OLD_PASS }
$oldAlias = if ($env:TOHID_KEY_ALIAS) { $env:TOHID_KEY_ALIAS } else { GradleDefault 'TOHID_KEY_ALIAS' }

$newAlias = if ($env:NEW_KEY_ALIAS) { $env:NEW_KEY_ALIAS } else { 'tohid2' }
$shown = $false
$newPass = $env:NEW_KEYSTORE_PASSWORD
if (-not $newPass) {
  $s1 = Read-Host 'رمزِ کلیدِ تازه (دست‌کم ۱۶ نویسه؛ Enter = تصادفی)' -AsSecureString
  $newPass = [System.Net.NetworkCredential]::new('', $s1).Password
  if (-not $newPass) {
    $chars = [char[]]'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $newPass = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    $shown = $true
  } else {
    $s2 = Read-Host 'دوباره' -AsSecureString
    if ([System.Net.NetworkCredential]::new('', $s2).Password -ne $newPass) { Die 'دو رمز یکی نیستند.' }
  }
}
if ($newPass.Length -lt 16) { Die 'رمزِ کلیدِ تازه کوتاه است (دست‌کم ۱۶ نویسه).' }
$env:NEW_PASS = $newPass

$next = Join-Path $outFull 'tohid-next.p12'
$lineage = Join-Path $outFull 'tohid-lineage.bin'
Write-Host '۱) ساختِ کلیدِ تازه…'
Invoke-Native { & $keytool -genkeypair -noprompt -keystore $next -storetype PKCS12 -alias $newAlias -keyalg RSA -keysize 4096 -validity 10950 `
  -dname 'CN=Daily Shop, OU=vil3ntec-it, O=vil3ntec-it, C=AF' -storepass:env NEW_PASS -keypass:env NEW_PASS 2>&1 | Out-Null }
if (-not (Test-Path $next)) { Die 'کلیدِ تازه ساخته نشد.' }

Write-Host '۲) دودمانِ v3 (کلیدِ پیشین ⇒ کلیدِ تازه)…'
Invoke-Native { & $apksigner rotate --out $lineage `
  --old-signer --ks $OldKeystore --ks-key-alias $oldAlias --ks-pass env:OLD_PASS --key-pass env:OLD_KEY_PASS `
  --new-signer --ks $next --ks-key-alias $newAlias --ks-pass env:NEW_PASS --key-pass env:NEW_PASS 2>&1 |
  Where-Object { "$_" -notmatch '^Picked up JAVA_TOOL_OPTIONS' } | Out-Host }
if (-not (Test-Path $lineage) -or (Get-Item $lineage).Length -eq 0) { Die 'دودمان ساخته نشد (رمز یا نامِ کلیدِ پیشین درست نیست؟ TOHID_KEYSTORE_PASSWORD/TOHID_KEY_ALIAS را بدهید).' }

function Fp([string]$ks, [string]$alias, [string]$passVar) {
  $line = Invoke-Native { & $keytool -list -v -keystore $ks -alias $alias -storepass:env $passVar 2>&1 } | ForEach-Object { "$_" } |
    Select-String -Pattern '^\s*SHA256:\s*([0-9A-F:]+)' | Select-Object -First 1
  if (-not $line) { return '' }
  return ($line.Matches[0].Groups[1].Value -replace ':', '').ToLowerInvariant()
}
$oldFp = Fp $OldKeystore $oldAlias 'OLD_PASS'
$newFp = Fp $next $newAlias 'NEW_PASS'
$check = (Invoke-Native { & $apksigner lineage --in $lineage --print-certs 2>&1 } | Out-String)
if (-not $oldFp -or -not $newFp -or ($check -notmatch $oldFp) -or ($check -notmatch $newFp)) { Die 'دودمانِ ساخته‌شده این دو گواهی را ندارد.' }

$ksB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($next))
$lnB64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($lineage))
Set-Content -NoNewline -Encoding ascii -Path (Join-Path $outFull 'TOHID_ROTATION_KEYSTORE_B64.txt') -Value $ksB64
Set-Content -NoNewline -Encoding ascii -Path (Join-Path $outFull 'TOHID_SIGNING_LINEAGE_B64.txt') -Value $lnB64
Set-Content -NoNewline -Encoding ascii -Path (Join-Path $outFull 'TOHID_ROTATION_KEY_ALIAS.txt') -Value $newAlias
Set-Content -NoNewline -Encoding ascii -Path (Join-Path $outFull 'TOHID_SIGNING_SHA256_EXTRA.txt') -Value $newFp
if ($shown) { Set-Content -NoNewline -Encoding ascii -Path (Join-Path $outFull 'TOHID_ROTATION_KEYSTORE_PASSWORD.txt') -Value $newPass }
Remove-Item Env:OLD_PASS, Env:OLD_KEY_PASS, Env:NEW_PASS -ErrorAction SilentlyContinue

$passNote = if ($shown) { "(تصادفی؛ در $outFull\TOHID_ROTATION_KEYSTORE_PASSWORD.txt)" } else { '(همان که زدید)' }
Write-Host ''
Write-Host "✅ ساخته شد در: $outFull   (فقط برای شما؛ هرگز در مخزن، ایمیل یا چت)" -ForegroundColor Green
Write-Host ''
Write-Host '۳) در GitHub ← مخزنِ shop ← Settings ← Secrets and variables ← Actions ← «Secrets» این پنج secret را بسازید:'
Write-Host "   TOHID_ROTATION_KEYSTORE_B64       = محتوای $outFull\TOHID_ROTATION_KEYSTORE_B64.txt"
Write-Host "   TOHID_SIGNING_LINEAGE_B64         = محتوای $outFull\TOHID_SIGNING_LINEAGE_B64.txt"
Write-Host "   TOHID_ROTATION_KEY_ALIAS          = $newAlias"
Write-Host "   TOHID_ROTATION_KEYSTORE_PASSWORD  = رمزِ کلیدِ تازه $passNote"
Write-Host '   TOHID_ROTATION_KEY_PASSWORD       = همان رمز'
Write-Host '   و در زبانهٔ «Variables» این متغیر (راز نیست):'
Write-Host "   TOHID_SIGNING_SHA256_EXTRA        = $newFp"
Write-Host ''
Write-Host "   اثرِ انگشتِ کلیدِ پیشین (برای مقایسه با نسخهٔ منتشرشده): $oldFp"
Write-Host ''
Write-Host 'مقدارِ دو secret ِ بلند (base64):'
Write-Host '--- TOHID_ROTATION_KEYSTORE_B64 ---'
Write-Host $ksB64
Write-Host '--- TOHID_SIGNING_LINEAGE_B64 ---'
Write-Host $lnB64
Write-Host '---'
if ($shown) { Write-Host "رمزِ تصادفیِ کلیدِ تازه (فقط همین یک بار؛ جایی امن یادداشت کنید): $newPass" -ForegroundColor Yellow }
Write-Host "۴) پوشهٔ $outFull را روی یک USB ِ جدا رونوشت کنید و از این کامپیوتر پاک کنید."
