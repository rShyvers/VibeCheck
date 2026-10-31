@echo off
rem Vibecheck - install (Windows). Extracts the bundled Temurin JRE 21 into .jre\
setlocal
set "JRE_DIR=.jre"
set "ARCHIVE=vendor\jre\windows-x64.zip"

if exist "%JRE_DIR%\bin\java.exe" goto done

if not exist "%ARCHIVE%" (
    echo error: %ARCHIVE% is missing from the repo (incomplete clone?).
    exit /b 1
)

rmdir /s /q "%JRE_DIR%" 2>nul
mkdir "%JRE_DIR%"
rem Windows 10+ ships bsdtar, which supports --strip-components
tar -xf "%ARCHIVE%" -C "%JRE_DIR%" --strip-components=1
if errorlevel 1 (
    echo error: failed to extract %ARCHIVE%
    exit /b 1
)

:done
echo Bundled JRE ready:
"%JRE_DIR%\bin\java.exe" -version
endlocal
