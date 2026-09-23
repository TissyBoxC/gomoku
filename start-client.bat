@echo off
setlocal
cd /d "%~dp0"

set "JAR=build\libs\gomoku-1.0.0.jar"
set "LIB=libs"

if not exist "%JAR%" (
    echo ERROR: %JAR% was not found.
    echo Run build-exe.bat or the local Gradle build first.
    pause
    exit /b 1
)

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

"%JAVA_EXE%" -version >nul 2>nul
if errorlevel 1 (
    echo ERROR: Java was not found. Install JDK 21 and set JAVA_HOME.
    pause
    exit /b 1
)

echo Starting Gomoku client...
"%JAVA_EXE%" -Dfile.encoding=UTF-8 -cp "%JAR%;%LIB%\*" io.github.tissyboxc.gomoku.Main
if errorlevel 1 (
    echo.
    echo ERROR: Client failed to start.
    pause
    exit /b 1
)

endlocal
