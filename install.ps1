# My Shop (فروشگاه من) — Windows installer bootstrap
#
#   irm https://vil3ntec-it.github.io/shop/install.ps1 | iex
#
# Downloads MyShop-Windows-Setup.exe (our server first, then GitHub), checks its
# SHA-256 against the published SHA256SUMS.txt and runs it. A file fetched this
# way is not marked "downloaded from the internet", so Windows does not show the
# blue SmartScreen "Windows protected your PC" box that browser downloads get
# (the installer is not code-signed yet). Nothing here needs Administrator.
#
# A dropped connection does not start the download over: Windows' own curl.exe
# continues from the bytes already on disk (--continue-at -), retries after
# 2, 4, 8, 16, 30 s pauses, and the partial file stays in
# %LOCALAPPDATA%\Tohid-setup-cache (named after its expected SHA-256, so two
# different files are never mixed) until the next run. No curl.exe (very old
# Windows 10) => Invoke-WebRequest as before. The SHA-256 check is unchanged.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}

if (-not [Environment]::Is64BitOperatingSystem) {
  Write-Host 'My Shop needs 64-bit Windows 10 or 11. On this PC use the web version: https://vil3ntec-it.github.io/shop/' -ForegroundColor Red
  return
}

$name = 'MyShop-Windows-Setup.exe'
$sources = @(
  'https://api.vill3n.top/api/releases/tohid-desktop/file',
  'https://github.com/vil3ntec-it/shop/releases/download/tohid-desktop'
)
# CI test hooks (tohid-desktop.yml): a local source and silent installer args
if ($env:TOHID_INSTALL_SRC) { $sources = @($env:TOHID_INSTALL_SRC) }
$dir = Join-Path $env:LOCALAPPDATA 'Tohid-setup-cache'
New-Item -ItemType Directory -Force $dir | Out-Null
$curl = Join-Path $env:SystemRoot 'System32\curl.exe'

function Get-Sha256([string]$file) {
  # .NET directly: Get-FileHash is missing on some systems (PowerShell 4, broken module paths)
  $fs = [IO.File]::OpenRead($file)
  try { return ([BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($fs)) -replace '-', '').ToLower() }
  finally { $fs.Dispose() }
}

function Get-Size([string]$file) {
  if (Test-Path -LiteralPath $file) { return (Get-Item -LiteralPath $file).Length }
  return 0
}

# Downloads $url into $file; returns only when $file has the SHA-256 $want.
function Save-Resumable([string]$url, [string]$file, [string]$want) {
  if ((Get-Size $file) -gt 0 -and (Get-Sha256 $file) -eq $want) { return }
  if (-not (Test-Path -LiteralPath $curl)) {
    Invoke-WebRequest -UseBasicParsing $url -OutFile $file
    if ((Get-Sha256 $file) -ne $want) { Remove-Item -LiteralPath $file -Force; throw 'file damaged in transit (SHA-256 mismatch)' }
    return
  }
  # curl's own --retry throws away the bytes of a failed attempt, so every
  # attempt is a fresh curl that continues from the size on disk
  $ErrorActionPreference = 'Continue'
  $pauses = @(2, 4, 8, 16, 30, 30, 30, 30)
  $fails = 0
  $restarts = 0
  while ($true) {
    $before = Get-Size $file
    if ($before -gt 0) { Write-Host ('  continuing from {0:N1} MB' -f ($before / 1MB)) }
    $code = & $curl --location --fail --silent --continue-at - --connect-timeout 20 --speed-limit 1024 --speed-time 60 `
      --user-agent tohid-install --write-out '%{http_code}' --output $file $url
    $rc = $LASTEXITCODE
    $done = ($rc -eq 0) -or ($rc -eq 22 -and "$code" -eq '416')
    if ($done -and (Get-Size $file) -gt 0) {
      if ((Get-Sha256 $file) -eq $want) { return }
      # complete but not the published file: start this file over (once)
      Remove-Item -LiteralPath $file -Force
      $restarts++
      if ($restarts -ge 2) { throw 'file damaged in transit (SHA-256 mismatch)' }
      continue
    }
    if ($rc -eq 33 -or $rc -eq 36) {
      # the server cannot continue a partial file: start over (once)
      Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue
      $restarts++
      if ($restarts -ge 2) { throw "server cannot resume (curl $rc)" }
      continue
    }
    if ($rc -eq 22 -and "$code" -match '^(401|403|404|410)$') { throw "HTTP $code" }
    if ($rc -eq 2 -or $rc -eq 23 -or $rc -eq 27) { throw "curl.exe cannot write here (curl $rc)" }
    if ((Get-Size $file) -gt $before) { $fails = 0 }
    $fails++
    if ($fails -gt $pauses.Count) { throw "connection kept dropping (curl $rc, HTTP $code); run the command again to continue" }
    $wait = $pauses[$fails - 1]
    Write-Host "  connection lost (curl $rc) - retry $fails of $($pauses.Count) in $wait s" -ForegroundColor Yellow
    Start-Sleep -Seconds $wait
  }
}

$ok = $false
$exe = $null
foreach ($src in $sources) {
  for ($try = 1; $try -le 2 -and -not $ok; $try++) {
    try {
      Write-Host "Downloading My Shop from $src ..."
      $sums = (Invoke-WebRequest -UseBasicParsing "$src/SHA256SUMS.txt").Content
      if ($sums -is [byte[]]) { $sums = [Text.Encoding]::UTF8.GetString($sums) }
      $line = ($sums -split "`n") | Where-Object { $_ -match "\s\*?$([regex]::Escape($name))\s*$" } | Select-Object -First 1
      if (-not $line) { throw 'checksum list has no entry for the installer' }
      $want = ($line.Trim() -split '\s+')[0].ToLower()
      if ($want -notmatch '^[0-9a-f]{64}$') { throw 'checksum list is malformed' }
      # the expected hash is in the name: a partial of another build is never continued
      $exe = Join-Path $dir "$($want.Substring(0, 16))-$name"
      Get-ChildItem -LiteralPath $dir -Filter "*-$name" -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -ne $exe } | Remove-Item -Force -ErrorAction SilentlyContinue
      Save-Resumable "$src/$name" $exe $want
      $ok = $true
    } catch {
      Write-Host "  failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
  }
  if ($ok) { break }
}
if (-not $ok) {
  Write-Host 'Could not download My Shop. Check the internet connection (VPN/proxy on or off) and run the command again.' -ForegroundColor Red
  return
}

Write-Host 'Verified. Starting the installer...' -ForegroundColor Green
if ($env:TOHID_INSTALL_ARGS) { Start-Process -FilePath $exe -ArgumentList $env:TOHID_INSTALL_ARGS -Wait }
else { Start-Process -FilePath $exe -Wait }
Remove-Item -LiteralPath $exe -Force -ErrorAction SilentlyContinue
