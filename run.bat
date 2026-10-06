@echo off
REM ---------------------------------------------------------------
REM  Personalized Travel Planner - one-click launcher (Windows)
REM  Uses the Maven Wrapper, so Maven does not need to be installed.
REM  Needs JDK 17 or newer: if one is installed in C:\Program Files\Java
REM  it is used, otherwise your JAVA_HOME is used.
REM ---------------------------------------------------------------
cd /d "%~dp0"

for /d %%D in ("C:\Program Files\Java\jdk-17*" "C:\Program Files\Java\jdk-2*") do set "JAVA_HOME=%%~D"

echo Using JAVA_HOME=%JAVA_HOME%
call "%~dp0mvnw.cmd" -q javafx:run
if errorlevel 1 pause
