@echo off
setlocal

echo --------------------------------------------
echo   Snakes and Ladders - Java Launcher
echo --------------------------------------------

where javac >nul 2>nul
if errorlevel 1 (
    echo ERROR: JDK not found.
    echo Please install Java JDK and add it to PATH.
    pause
    exit /b 1
)

echo Compiling...
javac SnakesAndLaddersGame.java
if errorlevel 1 (
    echo.
    echo Compile failed. Fix errors, then run this launcher again.
    pause
    exit /b 1
)

echo Starting game...
java SnakesAndLaddersGame

echo.
echo Game closed.
pause
