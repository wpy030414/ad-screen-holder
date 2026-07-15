#!/bin/sh
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@25/25.0.3/libexec/openjdk.jdk/Contents/Home"
export ANDROID_SDK_ROOT="/opt/homebrew/share/android-commandlinetools"
exec gradle "$@"
