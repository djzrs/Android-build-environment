# 编译环境：JDK + Android SDK 34。
# 由 .cnb.yml 的 docker.build 引用，构建结果推到仓库制品库缓存复用，
# 只有本文件（`by` 列表里的文件）变化时才重新构建。
FROM cnbcool/default-build-env:latest

# 底包是 Debian trixie，官方源里只有 openjdk-21，没有 17 了。
# 工程 jvmTarget 是 17，用 21 编译没有问题（本地实测通过）；
# JAVA_HOME 不写死路径，arm64 / amd64 都能覆盖。
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-21-jdk-headless unzip && \
    ln -sfn "$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")" /opt/java-home && \
    rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/java-home \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk

# commandlinetools 11076708 是 Google 长期可用的稳定版，内含 sdkmanager。
RUN mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" && \
    curl -fsSL -o /tmp/cmdline-tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools && \
    mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest" && \
    rm -rf /tmp/cmdline-tools /tmp/cmdline-tools.zip

# 只装工程实际需要的三个包，license 在镜像构建时一次性接受。
RUN yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null && \
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
      "platform-tools" "platforms;android-34" "build-tools;34.0.0" && \
    rm -rf "$ANDROID_SDK_ROOT/.downloadIntermediates" "$ANDROID_SDK_ROOT/.temp"

ENV PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"
