@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM FinLedger CLI launcher (FL-152) — Windows.
REM Usage: bin\finledger-cli.cmd [command…]
REM        No args → interactive shell (REPL).

set "SCRIPT_DIR=%~dp0"
REM trim trailing backslash for joining
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
for %%I in ("%SCRIPT_DIR%\..") do set "REPO_ROOT=%%~fI"

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
  ) else (
    set "JAVA=java"
  )
) else (
  set "JAVA=java"
)

set "JAR="
if defined FINLEDGER_CLI_JAR (
  if exist "%FINLEDGER_CLI_JAR%" (
    set "JAR=%FINLEDGER_CLI_JAR%"
  ) else (
    echo finledger-cli: FINLEDGER_CLI_JAR is set but not a file: %FINLEDGER_CLI_JAR% 1>&2
    exit /b 1
  )
)

if not defined JAR if exist "%SCRIPT_DIR%\finledger-cli.jar" set "JAR=%SCRIPT_DIR%\finledger-cli.jar"
if not defined JAR if exist "%REPO_ROOT%\finledger-cli.jar" set "JAR=%REPO_ROOT%\finledger-cli.jar"
if not defined JAR if exist "%REPO_ROOT%\finledger-cli\target\finledger-cli-0.1.0.jar" (
  set "JAR=%REPO_ROOT%\finledger-cli\target\finledger-cli-0.1.0.jar"
)

if not defined JAR (
  if exist "%REPO_ROOT%\mvnw.cmd" (
    echo finledger-cli: jar not found — building finledger-cli module… 1>&2
    pushd "%REPO_ROOT%"
    call mvnw.cmd -pl finledger-cli -am package -DskipTests -q
    popd
    if exist "%REPO_ROOT%\finledger-cli\target\finledger-cli-0.1.0.jar" (
      set "JAR=%REPO_ROOT%\finledger-cli\target\finledger-cli-0.1.0.jar"
    )
  )
)

if not defined JAR (
  echo finledger-cli: could not find the shaded CLI jar. 1>&2
  echo   Dev:  ensure Java 21+ and mvnw.cmd, or run from the FinLedger repo. 1>&2
  echo   Prod: place finledger-cli.jar next to this script, or set FINLEDGER_CLI_JAR. 1>&2
  exit /b 1
)

"%JAVA%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%
