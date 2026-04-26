# Android SDK
mkdir android-sdk
cd android-sdk
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/
sdkmanager "platform-tools"            "platforms;android-34"            "build-tools;34.0.0"
avdmanager create avd -n Pixel_6_API_34 -k "system-images;android-34;google_apis;x86_64"
# Update .bash_profile
export ANDROID_HOME="$HOME/Maison/bruno/prj/android-sdk"
export PATH=$PATH:$ANDROID_HOME/cmd-line/tools/latest/bin:$ANDROID_HOME/platform-tools
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-17.0.18.0.8-1.mga9.x86_64/
# Gradle
wget https://services.gradle.org/distributions/gradle-8.7-bin.zip
unzip gradle-8.7-bin.zip 
sudo mv gradle-8.7/bin/gradle /usr/local/bin
sudo mv gradle-8.7/lib/* /usr/local/lib/
gradle wrapper
# Devie emulator
../emulator/emulator --version
../emulator/emulator -avd Pixel_6_API_34
# Build & Install built app
./gradlew clean
./gradlew assembleDebug --stacktrace
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
# Clear logs
adb logcat -c
adb logcat | grep -i "FATAL\|AndroidRuntime\|E/Android"
adb push Livres.tc /sdcard/Download/
adb shell ls /sdcard/Download/
# Change version
Modify versionName in app/build.gradle.kts
check VERSION_NAME dans app/build/generated/source/buildConfig/debug/org/fdroid/tellicoviewer/BuildConfig.java
