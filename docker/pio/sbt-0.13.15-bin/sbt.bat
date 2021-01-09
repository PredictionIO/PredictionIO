@REM SBT launcher script
@REM 
@REM Environment:
@REM JAVA_HOME - location of a JDK home dir (mandatory)
@REM SBT_OPTS  - JVM options (optional)
@REM Configuration:
@REM sbtconfig.txt found in the SBT_HOME.

@REM   ZOMG! We need delayed expansion to build up CFG_OPTS later 
@setlocal enabledelayedexpansion

@echo off
set SBT_HOME=%~dp0
set SBT_ARGS=

rem FIRST we load the config file of extra options.
set FN=%SBT_HOME%\..\conf\sbtconfig.txt
set CFG_OPTS=
FOR /F "tokens=* eol=# usebackq delims=" %%i IN ("%FN%") DO (
  set DO_NOT_REUSE_ME=%%i
  rem ZOMG (Part #2) WE use !! here to delay the expansion of
  rem CFG_OPTS, otherwise it remains "" for this loop.
  set CFG_OPTS=!CFG_OPTS! !DO_NOT_REUSE_ME!
)

rem poor man's jenv (which is not available on Windows)
IF DEFINED JAVA_HOMES (
  IF EXIST .java-version FOR /F %%A IN (.java-version) DO (
    SET JAVA_HOME=%JAVA_HOMES%\%%A
    SET JDK_HOME=%JAVA_HOMES%\%%A
  )
)
rem must set PATH or wrong javac is used for java projects
IF DEFINED JAVA_HOME SET "PATH=%JAVA_HOME%\bin;%PATH%"

rem users can set JAVA_OPTS via .jvmopts (sbt-extras style)
IF EXIST .jvmopts FOR /F %%A IN (.jvmopts) DO (
  SET JAVA_OPTS=%%A !JAVA_OPTS!
)

rem We use the value of the JAVACMD environment variable if defined
set _JAVACMD=%JAVACMD%

if "%_JAVACMD%"=="" (
  if not "%JAVA_HOME%"=="" (
    if exist "%JAVA_HOME%\bin\java.exe" set "_JAVACMD=%JAVA_HOME%\bin\java.exe"
  )
)

if "%_JAVACMD%"=="" set _JAVACMD=java

rem We use the value of the JAVA_OPTS environment variable if defined, rather than the config.
set _JAVA_OPTS=%JAVA_OPTS%
if "%_JAVA_OPTS%"=="" set _JAVA_OPTS=%CFG_OPTS%

:args_loop
if "%~1" == "" goto args_end

if "%~1" == "-jvm-debug" (
  set JVM_DEBUG=true
  set /a JVM_DEBUG_PORT=5005 2>nul >nul
) else if "!JVM_DEBUG!" == "true" (
  set /a JVM_DEBUG_PORT=%1 2>nul >nul
  if not "%~1" == "!JVM_DEBUG_PORT!" (
    set SBT_ARGS=!SBT_ARGS! %1
  )
) else (
  set SBT_ARGS=!SBT_ARGS! %1
)

shift
goto args_loop
:args_end

if defined JVM_DEBUG_PORT (
  set _JAVA_OPTS=!_JAVA_OPTS! -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=!JVM_DEBUG_PORT!
)

call :process

call :checkjava

call :copyrt

call :sync_preloaded

call :run %SBT_ARGS%

if ERRORLEVEL 1 goto error
goto end

:run

"%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -cp "%SBT_HOME%sbt-launch.jar" xsbt.boot.Boot %*
goto :eof

:process
rem parses 1.7, 1.8, 9, etc out of java version "1.8.0_91"
"%_JAVACMD%" -Xmx512M -version 2> "%TEMP%\out.txt"
set JAVA_VERSION=0
>nul findstr /c:"version \"9" "%TEMP%\out.txt"
if /I %ERRORLEVEL% EQU 0 (set JAVA_VERSION=9)
>nul findstr /c:"version \"1.8" "%TEMP%\out.txt"
if /I %ERRORLEVEL% EQU 0 (set JAVA_VERSION=1.8)
>nul findstr /c:"version \"1.7" "%TEMP%\out.txt"
if /I %ERRORLEVEL% EQU 0 (set JAVA_VERSION=1.7)
>nul findstr /c:"version \"1.6" "%TEMP%\out.txt"
if /I %ERRORLEVEL% EQU 0 (set JAVA_VERSION=1.6)
>nul findstr /c:"version \"1.5" "%TEMP%\out.txt"
if /I %ERRORLEVEL% EQU 0 (set JAVA_VERSION=1.5)
exit /B 0

:checkjava
set required_version=1.6
if /I "%JAVA_VERSION%" GEQ "%required_version%" (
  exit /B 0
)
echo.
echo The java installation you have is not up to date
echo sbt requires at least version %required_version%+, you have
echo version %JAVA_VERSION%
echo.
echo Please go to http://www.java.com/getjava/ and download
echo a valid Java Runtime and install before running sbt.
echo.
exit /B 1

:copyrt
if /I "%JAVA_VERSION%" GEQ "9" (
  set rtexport="%SBT_HOME%java9-rt-export.jar"

  "%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -jar "%rtexport%" --rt-ext-dir > "%TEMP%.\rtext.txt"
  set /p java9_ext= < "%TEMP%.\rtext.txt"
  set java9_rt=%java9_ext%\rt.jar

  if not exist "%java9_rt%" (
    echo Copying runtime jar.
    mkdir "%java9_ext%"
    "%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -jar "%rtexport%" "%java9_rt%"
  )
  set _JAVA_OPTS=!_JAVA_OPTS! -Dscala.ext.dirs="%java9_ext%"
)
exit /B 0

:sync_preloaded
set PRELOAD_SBT_JAR="%UserProfile%\.sbt\preloaded\org.scala-sbt\sbt\%INIT_SBT_VERSION%\jars\sbt.jar"
if /I "%JAVA_VERSION%" GEQ "8" (
  where robocopy >nul 2>nul
  if %ERRORLEVEL% equ 0 (
    echo %PRELOAD_SBT_JAR%
    if not exist %PRELOAD_SBT_JAR% (
      if exist "%SBT_HOME%\..\lib\local-preloaded\" (
        echo 'about to robocopy'
        robocopy "%SBT_HOME%\..\lib\local-preloaded\" "%UserProfile%\.sbt\preloaded"
      )
    )
  )
)
exit /B 0

:error
@endlocal
exit /B 1

:end
@endlocal
exit /B 0
