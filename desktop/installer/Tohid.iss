; نصابِ ویندوزِ «فروشگاه من» — Inno Setup 6.5+، دو گونه از یک فایل:
;
;   Tohid-Windows-Setup.exe    (پیش‌فرض، ~۳ مگابایت) — «نصابِ آنلاین»
;   Tohid-Windows-Offline.exe  (/DOffline، ~۹۰ مگابایت) — همه‌چیز داخلِ خودش
;
; ── چرا آنلاین؟ (سناریوهای خطا، از گزارشِ صاحب مخزن) ─────────────────
; دو نصابِ کاملاً متفاوت (MSIِ WiX و Inno) روی کامپیوترِ او هر دو گفتند
; «فایل خراب است» («Data.cab is corrupt» و «The source file is corrupted»)،
; در حالی که فایلِ منتشرشده سالم بود و اندازهٔ فایلِ دانلودشده هم درست بود.
; یعنی بایت‌ها در راهِ دانلود خراب شده‌اند (دانلودمنیجری که تکه‌ها را بد
; وصل می‌کند، پروکسی/فیلترشکن، قطع و وصلِ شبکه) — نصابِ ۹۰ مگابایتی هر
; بار همان شانسِ خرابی را دارد. پس:
;   ۱. فایلِ دانلودیِ کاربر کوچک است (شانسِ خرابی تقریباً صفر)
;   ۲. خودِ نصاب برنامه را دانلود می‌کند و SHA-256 را می‌سنجد؛ خراب ⇐
;      پاک و دوباره، از سرورِ خودمان و بعد GitHub (هر کدام دو بار)
;   ۳. اگر Tohid-Windows-App-<نسخه>.7z کنارِ نصاب باشد (بی‌اینترنت)،
;      همان سنجیده و استفاده می‌شود
;   ۴. هیچ‌کدام نشد ⇐ پیامِ روشن و راهِ بعدی، نه خطای گنگ
;   ۵. برنامهٔ باز ⇐ بسته می‌شود؛ MSIِ قدیمی ⇐ بی‌صدا برداشته می‌شود
;   ۶. ویندوزِ ۳۲ بیتی یا قدیمی‌تر از ۱۰ ⇐ پیامِ روشن پیش از هر کار
; به‌روزرسانیِ درون‌برنامه نسخهٔ Offline را می‌گیرد (خودش SHA256SUMSِ
; امضاشده را می‌سنجد) و با ‎/SILENT‎ اجرا می‌کند.
;
; ساخت: tohid-desktop.yml — ‎iscc /DAppVersion=… /DPayloadSha=… /DPayloadSize=… /DPayloadBytes=… /DReleaseTag=… [/DOffline]‎
;
; ── دانلودِ ادامه‌دار (۱۴۰۵/۰۷/۲۶) ─────────────────────────────────────
; DownloadPageِ Inno نمی‌تواند ادامه دهد: هر قطع ⇐ ۹۰ مگابایت از صفر. حالا
; curl.exeِ خودِ ویندوز (۱۰ نسخهٔ ۱۸۰۳ به بعد و ۱۱) با ‎--continue-at -‎ در پوشهٔ
; ماندگارِ ‎{localappdata}\Tohid-setup-cache‎ می‌گیرد؛ قطع ⇐ curlِ تازه از همان
; بایت، با مکثِ ۲، ۴، ۸، ۱۶، ۳۰ ثانیه (۸ بار بی‌پیشرفت ⇐ نشانیِ بعد). نصابی که
; بسته شود، بارِ بعد از همان‌جا ادامه می‌دهد. ‎--retry‎ِ خودِ curl به کار نمی‌آید:
; بایت‌های تلاشِ شکست‌خورده را دور می‌ریزد. سنجشِ SHA-256 همان است. curl.exe
; نیست یا اجرا نمی‌شود ⇐ همان DownloadPageِ قبلی.
;
; ── کانال (F0-27) ─────────────────────────────────────────────────────
; ‎ReleaseTag‎ برچسبی است که این نصاب برایش ساخته شده (‎tohid-desktop‎ یا
; ‎tohid-desktop-beta‎). پیش‌تر نشانی همیشه ‎tohid-desktop‎ بود و نصابِ آنلاینِ
; آزمایشی بستهٔ نسخهٔ خودش را (که فقط زیرِ ‎-beta‎ است) پیدا نمی‌کرد.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\Tohid"
#endif
#ifndef PayloadSha
  #define PayloadSha ""
#endif
#ifndef PayloadSize
  #define PayloadSize "0"
#endif
; اندازهٔ خودِ ‎.7z‎ (برای نوارِ پیشرفت و «ادامه از X٪»)؛ ۰ ⇐ نامعلوم
#ifndef PayloadBytes
  #define PayloadBytes "0"
#endif
; بی ‎/DReleaseTag‎ ⇐ پایدار، مثلِ پیش
#ifndef ReleaseTag
  #define ReleaseTag "tohid-desktop"
#endif
#define PayloadName "Tohid-Windows-App-" + AppVersion + ".7z"

[Setup]
; ⛔ هرگز عوض نشود — ویندوز با همین شناسه نسخهٔ تازه را روی همان برنامه می‌نشاند
AppId={{8C2E4F1A-5B7D-4E3C-9A61-2F0D7B3E5C14}
AppName=فروشگاه من
AppVerName=فروشگاه من {#AppVersion}
AppVersion={#AppVersion}
VersionInfoVersion={#AppVersion}
AppPublisher=VIL3NTEC
AppPublisherURL=https://vil3ntec-it.github.io/shop/
AppSupportURL=https://vil3ntec-it.github.io/shop/download.html
AppCopyright=(c) VIL3NTEC
DefaultDirName={localappdata}\Tohid
DefaultGroupName=فروشگاه من
DisableProgramGroupPage=yes
DisableDirPage=auto
DisableWelcomePage=no
UsePreviousAppDir=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
WizardStyle=modern
WizardSizePercent=110
WizardImageFile=wizard-164.bmp,wizard-205.bmp,wizard-246.bmp
WizardSmallImageFile=wizard-small-55.bmp,wizard-small-69.bmp,wizard-small-83.bmp
WizardImageStretch=no
SetupIconFile=..\icons\tohid.ico
UninstallDisplayIcon={app}\Tohid.exe
UninstallDisplayName=فروشگاه من
CloseApplications=force
RestartApplications=no
SetupLogging=yes
ShowLanguageDialog=no
OutputDir=..\build\installer
#ifdef Offline
OutputBaseFilename=Tohid-Windows-Offline
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
#else
OutputBaseFilename=Tohid-Windows-Setup
Compression=lzma2/max
ArchiveExtraction=enhanced/nopassword
#endif

[Messages]
WelcomeLabel2=This will install [name/ver] on your computer.%n%nYour shop ledger, on the same account as your phone - every device stays in sync.%n%nIf an older version is installed it is updated in place; your data and settings are kept.

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; پوشه‌های برنامه با هر نسخه کامل عوض می‌شوند — فایلِ کهنه از نسخهٔ قبل نمی‌ماند
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
#ifdef Offline
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
#else
Source: "{tmp}\{#PayloadName}"; DestDir: "{app}"; ExternalSize: {#PayloadSize}; Flags: external extractarchive recursesubdirs createallsubdirs ignoreversion
#endif

[UninstallDelete]
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Icons]
Name: "{autoprograms}\فروشگاه من"; Filename: "{app}\Tohid.exe"
Name: "{autodesktop}\فروشگاه من"; Filename: "{app}\Tohid.exe"; Tasks: desktopicon

[Run]
; نصبِ دستی: تیکِ «Launch»؛ به‌روزرسانیِ بی‌صدا: خودکار باز می‌شود (آزمونِ CI با ‎/NOLAUNCH‎ نه)
Filename: "{app}\Tohid.exe"; Description: "{cm:LaunchProgram,فروشگاه من}"; Flags: nowait postinstall; Check: MayLaunch

[Code]
const
  // نسخهٔ قدیمی (MSIِ jpackage، همان upgradeUuidِ build.gradle.kts)
  OldUpgradeCode = '{6F1E0C52-7A3B-4D8E-9C1F-3B2A5D7E9F10}';

function MsiEnumRelatedProducts(lpUpgradeCode: String; dwReserved: Cardinal; iProductIndex: Cardinal; lpProductBuf: String): Cardinal;
  external 'MsiEnumRelatedProductsW@msi.dll stdcall';

function MayLaunch: Boolean;
var
  I: Integer;
begin
  Result := True;
  for I := 1 to ParamCount do
    if CompareText(ParamStr(I), '/NOLAUNCH') = 0 then Result := False;
end;

#ifndef Offline
const
  PayloadName = '{#PayloadName}';
  PayloadSha = '{#PayloadSha}';
  PayloadBytes = {#PayloadBytes};
  ReleaseTag = '{#ReleaseTag}';

var
  DownloadPage: TDownloadWizardPage;
  ResumePage: TOutputProgressWizardPage;
  CurlBroken: Boolean;

procedure InitializeWizard;
begin
  DownloadPage := CreateDownloadPage('Downloading فروشگاه من', 'Setup is downloading and verifying the program files...', nil);
  DownloadPage.ShowBaseNameInsteadOfUrl := True;
  ResumePage := CreateOutputProgressPage('Downloading فروشگاه من',
    'If the connection drops, setup waits and continues where it stopped - it does not start over.');
end;

// پوشهٔ ماندگارِ دانلود — جدا از {app} (که پیش‌فرضش {localappdata}\Tohid است)
function CacheDir: String;
begin
  Result := ExpandConstant('{localappdata}\Tohid-setup-cache');
end;

// curl.exe همراهِ ویندوزِ ۱۰ (۱۸۰۳ به بعد) و ۱۱
function CurlExe: String;
begin
  Result := ExpandConstant('{sys}\curl.exe');
  if not FileExists(Result) then Result := '';
end;

function SizeOfFile(const Path: String): Integer;
var
  Size: Integer;
begin
  Result := 0;
  if FileSize(Path, Size) then Result := Size;
end;

function PayloadIsGood(const Path: String): Boolean;
begin
  Result := False;
  if not FileExists(Path) then Exit;
  try
    Result := CompareText(GetSHA256OfFile(Path), PayloadSha) = 0;
  except
    Result := False;
  end;
end;

function Pct(const Have: Integer): Integer;
begin
  Result := 0;
  if PayloadBytes > 1024 then Result := (Have div 1024) * 100 div (PayloadBytes div 1024);
  if Result > 100 then Result := 100;
end;

procedure ShowProgress(const Path, Note: String);
var
  Have: Integer;
begin
  Have := SizeOfFile(Path);
  if PayloadBytes > 1024 then begin
    ResumePage.SetText(Note, Format('%d of %d MB', [Have div 1048576, PayloadBytes div 1048576]));
    ResumePage.SetProgress(Have div 1024, PayloadBytes div 1024);
  end else
    ResumePage.SetText(Note, Format('%d MB', [Have div 1048576]));
end;

// مکث با پنجرهٔ زنده (SetText/SetProgress پیام‌های ویندوز را پردازش می‌کنند)
procedure PauseShowing(const Seconds: Integer; const Path, Note: String);
var
  I: Integer;
begin
  for I := 1 to Seconds * 4 do begin
    ShowProgress(Path, Note);
    Sleep(250);
  end;
end;

// بسته‌های نسخه‌های دیگر در پوشهٔ ماندگار نمی‌مانند (فایل‌های install.ps1 دست نمی‌خورند)
procedure ForgetOtherVersions;
var
  F: TFindRec;
begin
  if FindFirst(CacheDir + '\Tohid-Windows-App-*', F) then
    try
      repeat
        if ((F.Attributes and FILE_ATTRIBUTE_DIRECTORY) = 0) and (Pos(PayloadName, F.Name) <> 1) then
          DeleteFile(CacheDir + '\' + F.Name);
      until not FindNext(F);
    finally
      FindClose(F);
    end;
end;

// curl.exe پنهان از راهِ cmd.exe اجرا می‌شود تا پنجره زنده بماند و پیشرفت
// دیده شود؛ کدِ خروجش در <فایل>.rc نوشته می‌شود. ‎-1‎ ⇐ اجرا نشد.
function RunCurl(const Url, Dest, Note: String): Integer;
var
  Marker, Line: String;
  Rc, I, Idle, Last, Have: Integer;
  Code: AnsiString;
begin
  Result := -1;
  Marker := Dest + '.rc';
  DeleteFile(Marker);
  Line := '"' + CurlExe + '" --location --fail --silent --show-error --continue-at - --connect-timeout 20' +
    ' --speed-limit 1024 --speed-time 60 --user-agent tohid-setup --output "' + Dest + '" "' + Url + '"' +
    ' 2>"' + Dest + '.err" & (echo !errorlevel!)>"' + Marker + '"';
  if not Exec(ExpandConstant('{cmd}'), '/D /S /V:ON /C "' + Line + '"', '', SW_HIDE, ewNoWait, Rc) then begin
    Log('curl.exe could not be started: ' + SysErrorMessage(Rc));
    Exit;
  end;
  Idle := 0;
  Last := -1;
  // curl خودش با ‎--speed-time‎ و ‎--connect-timeout‎ تمام می‌شود؛ این سقف فقط
  // برای وقتی است که cmd نشانه را ننویسد (۵ دقیقه بی هیچ بایتِ تازه)
  while not FileExists(Marker) do begin
    Sleep(250);
    ShowProgress(Dest, Note);
    Have := SizeOfFile(Dest);
    if Have = Last then Idle := Idle + 1 else Idle := 0;
    Last := Have;
    if Idle > 1200 then begin
      Log('curl.exe did not report back; giving up on it');
      Exit;
    end;
  end;
  for I := 1 to 40 do begin
    if LoadStringFromFile(Marker, Code) and (Trim(String(Code)) <> '') then begin
      Result := StrToIntDef(Trim(String(Code)), -1);
      Exit;
    end;
    Sleep(100);
  end;
end;

// Url ⇐ Dest، با ادامه از آنچه روی دیسک است. True فقط وقتی Dest کامل است و
// SHA-256اش درست. قطع ⇐ curlِ تازه از همان بایت، با مکثِ ۲، ۴، ۸، ۱۶، ۳۰… ثانیه؛
// ۸ بار پشتِ هم بی هیچ بایتِ تازه ⇐ False (نشانیِ بعد).
function CurlDownload(const Url, Dest: String; var LastError: String): Boolean;
var
  Fails, Restarts, Rc, Before, After, Secs: Integer;
  Err: AnsiString;
  Note: String;
begin
  Result := False;
  Fails := 0;
  Restarts := 0;
  while True do begin
    Before := SizeOfFile(Dest);
    if (PayloadBytes > 0) and (Before > PayloadBytes) then begin
      DeleteFile(Dest);
      Before := 0;
    end;
    if (Before > 0) and ((PayloadBytes <= 0) or (Before = PayloadBytes)) then begin
      if PayloadIsGood(Dest) then begin
        Result := True;
        Exit;
      end;
      if Before = PayloadBytes then begin
        Log('Cached payload is complete but has a wrong SHA-256; starting it over');
        DeleteFile(Dest);
        Before := 0;
      end;
    end;
    if Before > 0 then begin
      Note := Format('Resuming from %d%% - a dropped connection continues where it stopped.', [Pct(Before)]);
      Log(Format('Resuming %s from byte %d', [Url, Before]));
    end else
      Note := 'Downloading - a dropped connection continues where it stopped.';

    Rc := RunCurl(Url, Dest, Note);
    After := SizeOfFile(Dest);
    Err := '';
    if not LoadStringFromFile(Dest + '.err', Err) then Err := '';
    LastError := Trim(String(Err));
    if LastError = '' then LastError := Format('curl.exe exit code %d', [Rc]);
    Log(Format('curl.exe exit %d, %d -> %d bytes: %s', [Rc, Before, After, LastError]));

    // تمام شد (یا ۴۱۶: از قبل کامل بود) ⇐ سنجش
    if (Rc = 0) or ((Rc = 22) and (Pos('error: 416', LastError) > 0)) then begin
      if PayloadIsGood(Dest) then begin
        Result := True;
        Exit;
      end;
      Log('SHA-256 mismatch: starting this file over');
      DeleteFile(Dest);
      LastError := 'The downloaded file failed the SHA-256 check.';
      Restarts := Restarts + 1;
      if Restarts >= 2 then Exit;
      Continue;
    end;
    // خودِ curl این‌جا کار نمی‌کند ⇐ دانلودکنندهٔ Inno
    if (Rc = -1) or (Rc = 2) or (Rc = 23) or (Rc = 27) then begin
      CurlBroken := True;
      Exit;
    end;
    // سرور ادامه نمی‌دهد ⇐ همین فایل از صفر، فقط یک بار
    if (Rc = 33) or (Rc = 36) then begin
      DeleteFile(Dest);
      Restarts := Restarts + 1;
      if Restarts >= 2 then Exit;
      Continue;
    end;
    // این نشانی این فایل را ندارد ⇐ نشانیِ بعد (نیمه می‌ماند)
    if (Rc = 1) or (Rc = 3) or ((Rc = 22) and ((Pos('error: 404', LastError) > 0) or
       (Pos('error: 403', LastError) > 0) or (Pos('error: 410', LastError) > 0))) then Exit;

    if After > Before then Fails := 0;
    Fails := Fails + 1;
    if Fails > 8 then Exit;
    case Fails of
      1: Secs := 2;
      2: Secs := 4;
      3: Secs := 8;
      4: Secs := 16;
    else
      Secs := 30;
    end;
    PauseShowing(Secs, Dest, Format('Connection lost - try %d of 8 in %d s (resuming from %d%%).', [Fails, Secs, Pct(After)]));
  end;
end;

// پس از نصب، دانلودِ ماندگار لازم نیست
procedure CurStepChanged(CurStep: TSetupStep);
var
  Cached: String;
begin
  if CurStep <> ssPostInstall then Exit;
  Cached := CacheDir + '\' + PayloadName;
  DeleteFile(Cached);
  DeleteFile(Cached + '.err');
  DeleteFile(Cached + '.rc');
  RemoveDir(CacheDir);
end;

// ۳: فایلِ برنامه کنارِ نصاب (نصبِ بی‌اینترنت) — فقط اگر SHA-256اش درست باشد
function UseLocalPayload: Boolean;
var
  Local: String;
begin
  Result := False;
  Local := ExpandConstant('{src}\') + PayloadName;
  if not FileExists(Local) then Exit;
  if CompareText(GetSHA256OfFile(Local), PayloadSha) <> 0 then begin
    Log('Local payload has a wrong SHA-256, ignoring: ' + Local);
    Exit;
  end;
  Result := CopyFile(Local, ExpandConstant('{tmp}\') + PayloadName, False);
  Log('Using local payload: ' + Local);
end;

// ۲: سرورِ خودمان، بعد GitHub — زیرِ برچسبی که این نصاب برایش ساخته شده (ReleaseTag).
//   ۲الف) curl.exe، ادامه‌دار، در پوشهٔ ماندگار؛ نیمه از نشانیِ اول با نشانیِ دوم
//         ادامه می‌یابد (همان فایل؛ SHA-256 در آخر می‌سنجد).
//   ۲ب) فقط وقتی curl.exe نیست یا این‌جا کار نمی‌کند: DownloadPageِ Inno، هر
//       نشانی دو بار؛ فایلِ با hashِ نادرست را رد می‌کند (استثنا) و از نو می‌گیرد.
function DownloadPayload(var LastError: String): Boolean;
var
  Urls: TArrayOfString;
  I, Try_: Integer;
  Custom, Cached: String;
begin
  Result := False;
  Custom := ExpandConstant('{param:PAYLOADURL|}');
  if Custom <> '' then begin
    SetArrayLength(Urls, 1);
    Urls[0] := Custom;
  end else begin
    SetArrayLength(Urls, 2);
    Urls[0] := 'https://api.vill3n.top/api/releases/' + ReleaseTag + '/file/' + PayloadName;
    Urls[1] := 'https://github.com/vil3ntec-it/shop/releases/download/' + ReleaseTag + '/' + PayloadName;
  end;
  Log('Release tag: ' + ReleaseTag);

  // ۲الف — نامِ کوتاهِ ۸.۳ِ پوشه: curl.exeِ ویندوز نامِ کاربرِ فارسی را در مسیر
  // نمی‌فهمد. ‎!‎ و ‎%‎ در خطِ cmd معنا دارند؛ با آن‌ها همان راهِ ۲ب.
  CurlBroken := (CurlExe = '') or not ForceDirectories(CacheDir);
  Cached := GetShortName(CacheDir) + '\' + PayloadName;
  if (Pos('!', Cached) > 0) or (Pos('%', Cached) > 0) then CurlBroken := True;
  if not CurlBroken then begin
    ForgetOtherVersions;
    ResumePage.Show;
    try
      for I := 0 to GetArrayLength(Urls) - 1 do begin
        if (Pos('!', Urls[I]) > 0) or (Pos('%', Urls[I]) > 0) then Continue;
        if CurlDownload(Urls[I], Cached, LastError) then begin
          Result := CopyFile(Cached, ExpandConstant('{tmp}\') + PayloadName, False);
          if not Result then LastError := 'The downloaded file could not be copied.';
          Exit;
        end;
        if CurlBroken then Break;
      end;
    finally
      ResumePage.Hide;
    end;
    if not CurlBroken then Exit;
  end;
  Log('curl.exe is missing or unusable here; using the built-in downloader (no resume)');

  // ۲ب
  for I := 0 to GetArrayLength(Urls) - 1 do
    for Try_ := 1 to 2 do begin
      DownloadPage.Clear;
      DownloadPage.Add(Urls[I], PayloadName, PayloadSha);
      DownloadPage.Show;
      try
        try
          DownloadPage.Download;
          Result := True;
          Exit;
        except
          if DownloadPage.AbortedByUser then begin
            LastError := 'Download cancelled.';
            Exit;
          end;
          LastError := GetExceptionMessage;
          Log(Format('Download failed (%s, try %d): %s', [Urls[I], Try_, LastError]));
          DeleteFile(ExpandConstant('{tmp}\') + PayloadName);
        end;
      finally
        DownloadPage.Hide;
      end;
    end;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Err: String;
begin
  Result := True;
  if CurPageID <> wpReady then Exit;
  if UseLocalPayload then Exit;
  if not DownloadPayload(Err) then begin
    SuppressibleMsgBox(
      'فروشگاه من could not be downloaded.' + #13#10#13#10 +
      'Reason: ' + Err + #13#10#13#10 +
      'Please check your internet connection (or turn your VPN/proxy off or on) and click Install again.' + #13#10#13#10 +
      'Without internet: download MyShop-Windows-Offline.exe from' + #13#10 +
      'https://vil3ntec-it.github.io/shop/download.html',
      mbCriticalError, MB_OK, IDOK);
    Result := False;
  end;
end;
#endif

// ۵: نسخهٔ MSIِ قدیمی را بی‌صدا برمی‌دارد تا دو «Tohid» در فهرستِ
// برنامه‌ها نماند. فقط فایل‌های برنامه — دفتر و تنظیمات دست نمی‌خورند.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Code: String;
  Rc, Guard: Integer;
begin
  Result := '';
  Guard := 0;
  repeat
    Code := StringOfChar(' ', 39);
    if MsiEnumRelatedProducts(OldUpgradeCode, 0, 0, Code) <> 0 then Break;
    Code := Copy(Code, 1, 38);
    Log('Removing old MSI version: ' + Code);
    if Guard = 0 then
      Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM Tohid.exe', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Exec(ExpandConstant('{sys}\msiexec.exe'), '/x ' + Code + ' /qn /norestart', '', SW_HIDE, ewWaitUntilTerminated, Rc);
    Guard := Guard + 1;
  until Guard >= 3;
end;
