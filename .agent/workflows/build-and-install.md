---
description: How to build and install the NSFW Shield app on a connected Android device
---
1. Ensure your Android device is connected via USB and ADB is enabled.
2. Open a terminal in the project root directory.
3. Build the debug APK (with auto-configured Java):
// turbo
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; $env:Path += ";C:\Program Files\Android\Android Studio\jbr\bin"; ./gradlew assembleDebug
```
4. Install the APK on the connected device (with auto-configured ADB):
// turbo
```powershell
& "C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```
