@echo off

IF [%1] == [] GOTO DEFAULT_PORT
SET PORT=%1
GOTO CHECK_PORT
:DEFAULT_PORT
SET PORT=50000

:CHECK_PORT
netstat -o -n -a | findstr 0.0.0.0:%PORT% > nul
IF NOT %ERRORLEVEL% EQU 0 (GOTO START_APP)
echo Meriken's 2ch Browser is already running.
GOTO OPEN_BROWSER

:START_APP
start java -jar merikens-2ch-browser.jar %PORT%

:WAIT_FOR_APP
ping 192.0.2.2 -n 1 -w 100 > nul
netstat -o -n -a | findstr 0.0.0.0:%PORT% > nul
if not %ERRORLEVEL% equ 0 (@GOTO WAIT_FOR_APP)

:OPEN_BROWSER
start "" "http://localhost:%PORT%/"
 