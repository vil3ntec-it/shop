@echo off
chcp 65001 >nul
rem «فروشگاه من» — نصب روی ویندوز (۶۴ بیت)
if /I not "%PROCESSOR_ARCHITECTURE%"=="AMD64" if /I not "%PROCESSOR_ARCHITEW6432%"=="AMD64" if /I not "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
  echo این ویندوز ۳۲ بیتی است و برنامهٔ «فروشگاه من» ۶۴ بیتی است.
  echo نسخهٔ مرورگری: https://vil3ntec-it.github.io/shop/
  pause
  exit /b 1
)
start "" "%~dp0Windows\MyShop-Windows-Setup.exe"
