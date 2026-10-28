FROM gradle:8.11.1-jdk21@sha256:7990a44ed0ad609ee740426d3becc69ae7d10a5ed14da7e354ad83cf7ef1d087 AS sdk
USER root
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}
RUN mkdir -p /opt/android-sdk/cmdline-tools && curl -fsSL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip -o /tmp/sdk.zip && echo "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583 /tmp/sdk.zip" | sha256sum -c - && unzip -q /tmp/sdk.zip -d /tmp/sdksetup && mv /tmp/sdksetup/cmdline-tools /opt/android-sdk/cmdline-tools/latest && rm -rf /tmp/sdk.zip /tmp/sdksetup
RUN yes | sdkmanager --licenses > /tmp/licenses.log
RUN sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /tmp/sdk-install.log 2>&1 || (cat /tmp/sdk-install.log && exit 1)
RUN mkdir -p /project /home/gradle/.android && chown -R gradle:gradle /project /home/gradle/.android
USER gradle
WORKDIR /project

FROM sdk AS build
COPY --chown=gradle:gradle . .
CMD ["sh", "scripts/build.sh"]
