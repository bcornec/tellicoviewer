mkdir android-sdk
cd android-sdk
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/
sdkmanager "platform-tools"            "platforms;android-34"            "build-tools;34.0.0"
avdmanager create avd -n Pixel_6_API_34 -k "system-images;android-34;google_apis;x86_64"
wget https://services.gradle.org/distributions/gradle-8.7-bin.zip
unzip gradle-8.7-bin.zip 
sudo mv gradle-8.7/bin/gradle /usr/local/bin
sudo mv gradle-8.7/lib/* /usr/local/lib/
gradle wrapper
avdmanager create avd -n Pixel_6_API_34 -k "system-images;android-34;google_apis;x86_64"
./gradlew clean
./gradlew assembleDebug --stacktrace
../emulator/emulator --version
../emulator/emulator -avd Pixel_6_API_34
adb install -r ./app/build/outputs/apk/debug/app-debug.apk
# Clear logs
adb logcat -c
adb logcat | grep -i "FATAL\|AndroidRuntime\|E/Android"
adb push Livres.tc /sdcard/Download/
adb shell ls /sdcard/Download/
