@echo off
setlocal
set "ROOT=%~dp0"
if not exist "%ROOT%gradle\wrapper\gradle-wrapper.jar" (
  echo [Philco Lite] Gradle Wrapper JAR ainda nao configurado.
  echo Execute no PowerShell: .\scripts\preparar-wrapper.ps1
  exit /b 1
)
if defined JAVA_HOME (
    set "JAVAEXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVAEXE=java.exe"
)
"%JAVAEXE%" -Dorg.gradle.appname=gradlew -classpath "%ROOT%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
endlocal
