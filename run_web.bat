@echo off
echo ===================================================
echo   E-Zorax: Employee Payroll Management System (Web)
echo ===================================================
if not exist out mkdir out

echo Compiling Java source files...
javac -cp "lib/*" -d out src/db/*.java src/model/*.java src/service/*.java src/app/*.java
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Starting Web Server on http://localhost:8080 ...
echo Open your browser at: http://localhost:8080
echo Press Ctrl+C in this terminal to stop the server.
java -cp "out;lib/*" app.WebServer
