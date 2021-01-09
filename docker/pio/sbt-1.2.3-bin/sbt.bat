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
  SET _jvmopts_line=%%A
  IF NOT "!_jvmopts_line:~0,1!"=="#" (
    SET JAVA_OPTS=%%A !JAVA_OPTS!
  )
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

set INIT_SBT_VERSION=1.2.3

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
) else if /I "%~1" == "new" (
  set sbt_new=true
  set SBT_ARGS=!SBT_ARGS! %1
) else (
  set SBT_ARGS=!SBT_ARGS! %1
)

shift
goto args_loop
:args_end

rem Confirm a user's intent if the current directory does not look like an sbt
rem top-level directory and the "new" command was not given.
if not exist build.sbt (
  if not exist project\ (
    if not defined sbt_new (
      echo [warn] Neither build.sbt nor a 'project' directory in the current directory: %CD%
      setlocal
:confirm
      echo c^) continue
      echo q^) quit

      set /P reply=?^ 
      if /I "!reply!" == "c" (
        goto confirm_end
      ) else if /I "!reply!" == "q" (
        exit /B 1
      )

      goto confirm
:confirm_end
      endlocal
    )
  )
)

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
rem Parses x out of 1.x; for example 8 out of java version 1.8.0_xx
rem Otherwise, parses the major version; 9 out of java version 9-ea
set JAVA_VERSION=0
for /f "tokens=3" %%g in ('"%_JAVACMD%" -Xms32M -Xmx32M -version 2^>^&1 ^| findstr /i version') do (
  set JAVA_VERSION=%%g
)
set JAVA_VERSION=%JAVA_VERSION:"=%
for /f "delims=.-_ tokens=1-2" %%v in ("%JAVA_VERSION%") do (
  if /I "%%v" EQU "1" (
    set JAVA_VERSION=%%w
  ) else (
    set JAVA_VERSION=%%v
  )
)
exit /B 0

:checkjava
set required_version=6
if /I %JAVA_VERSION% GEQ %required_version% (
  exit /B 0
)
echo.
echo The Java Development Kit (JDK) installation you have is not up to date.
echo sbt requires at least version %required_version%+, you have
echo version %JAVA_VERSION%
echo.
echo Please go to http://www.oracle.com/technetwork/java/javase/downloads/ and download
echo a valid JDK and install before running sbt.
echo.
exit /B 1

:copyrt
if /I %JAVA_VERSION% GEQ 9 (
  set rtexport=!SBT_HOME!java9-rt-export.jar

  "%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -jar "!rtexport!" --rt-ext-dir > "%TEMP%.\rtext.txt"
  set /p java9_ext= < "%TEMP%.\rtext.txt"
  set java9_rt=!java9_ext!\rt.jar

  if not exist "!java9_rt!" (
    mkdir "!java9_ext!"
    "%_JAVACMD%" %_JAVA_OPTS% %SBT_OPTS% -jar "!rtexport!" "!java9_rt!"
  )
  set _JAVA_OPTS=!_JAVA_OPTS! -Dscala.ext.dirs="!java9_ext!"

  rem check to see if a GC has been set in the opts
  echo !_JAVA_OPTS! | findstr /r "Use.*GC" >nul
  if ERRORLEVEL 1 (
    rem don't have a GC set - revert to old GC
    set _JAVA_OPTS=!_JAVA_OPTS! -XX:+UseParallelGC
  )
)
exit /B 0

:sync_preloaded
if "%INIT_SBT_VERSION%"=="" (
  rem FIXME: better %INIT_SBT_VERSION% detection
  FOR /F "tokens=* USEBACKQ" %%F IN (`dir /b "%SBT_HOME%\..\lib\local-preloaded\org.scala-sbt\sbt" /B`) DO (
    SET INIT_SBT_VERSION=%%F
  )
)
set PRELOAD_SBT_JAR="%UserProfile%\.sbt\preloaded\org.scala-sbt\sbt\%INIT_SBT_VERSION%\jars\sbt.jar"
if /I %JAVA_VERSION% GEQ 8 (
  where robocopy >nul 2>nul
  if %ERRORLEVEL% equ 0 (
    REM echo %PRELOAD_SBT_JAR%
    if not exist %PRELOAD_SBT_JAR% (
      if exist "%SBT_HOME%\..\lib\local-preloaded\" (
        echo "about to robocopy"
        robocopy "%SBT_HOME%\..\lib\local-preloaded" "%UserProfile%\.sbt\preloaded" /E
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
