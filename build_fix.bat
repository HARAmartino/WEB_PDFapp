@echo off
set GRADLE_USER_HOME=C:\Users\sheng\.gradle-webpdfapp-cache
call .\gradlew.bat assembleDebug --no-daemon --console=plain > build_output_clean21.txt 2>&1
echo Exit code: %ERRORLEVEL%
