@echo off
rem Vibecheck - run (Windows)
setlocal
call install.bat || exit /b 1
.jre\bin\java.exe -cp out Main
endlocal
