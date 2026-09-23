@echo off
setlocal
cd /d "%~dp0"

set "GRADLE_HOME=D:\service\gradle-9.4.1"
set "GRADLE_USER_HOME=D:\service\gradle-9.4.1\gradle_reop"
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
    echo ERROR: gradle.bat was not found at %GRADLE_HOME%\bin\gradle.bat.
    pause
    exit /b 1
)

where jpackage >nul 2>nul
if errorlevel 1 (
    echo ERROR: jpackage was not found. Please use JDK 21 instead of JRE.
    pause
    exit /b 1
)

echo Building Windows EXE files with the local Gradle and offline cache...
call "%GRADLE_HOME%\bin\gradle.bat" --offline --no-daemon clean packageExe packageServerExe
if errorlevel 1 (
    echo.
    echo ERROR: EXE build failed. Check the local Gradle cache and JDK 21.
    pause
    exit /b 1
)

echo.
echo Client: build\package\Gomoku\Gomoku.exe
echo Server: build\package-server\GomokuServer\GomokuServer.exe
pause
endlocal
