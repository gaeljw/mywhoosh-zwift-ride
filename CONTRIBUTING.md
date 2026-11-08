# Contributing to RideBridge

RideBridge is a personal project, but issues and pull requests are welcome. Please keep the scope in mind: Zwift Ride
shift buttons to MyWhoosh gear taps, on one phone, with no third-party dependencies.

The user-facing documentation (install, first run, calibration) is in the [README](README.md).

## Build from source (Fedora)

No Android Studio needed; the command-line SDK is enough.

```bash
# JDK 17 to 21 (newer JDKs such as 25 are not supported by this Gradle/AGP version)
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk

# Android command-line tools: download "Command line tools only" for Linux from
# https://developer.android.com/studio and unzip into ~/Android/Sdk/cmdline-tools/latest
export ANDROID_HOME=~/Android/Sdk
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

echo "sdk.dir=$ANDROID_HOME" > local.properties    # machine-specific, git-ignored
./gradlew assembleDebug                              # the Gradle wrapper is included
```

`adb` is installed by `platform-tools` (`$ANDROID_HOME/platform-tools/adb`); no `dnf install` is required.

Install on the phone (USB debugging on):

```bash
$ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## CI and releases (GitHub Actions)

- **CI** (`.github/workflows/ci.yml`): on every push to `main` and every pull request, checks that the project builds
  (`assembleDebug`). It can also be started by hand from the *Actions* tab ("Run workflow"). No APK is kept: releases are
  the way to get one.
- **Release** (`.github/workflows/release.yml`): pushing a tag that starts with `v` builds a **signed** release APK and
  attaches it to a GitHub release (`ridebridge-<tag>.apk`):

  ```bash
  git tag v0.2 && git push origin v0.2
  ```

  If a release for that tag already exists (for instance one you created by hand on GitHub, which creates the tag
  too), the APK is attached to it and its title and notes are left untouched. Otherwise the release is created with
  generated notes. A *draft* release has no tag yet, so nothing runs until you publish it.

  The app's version name is the tag without the `v`, and the version code is derived from it (see below).
- **Build APK** (`.github/workflows/build-apk.yml`): started by hand (*Actions* tab, or `gh workflow run build-apk.yml`),
  builds a **signed** APK of the current `main` and uploads it as a workflow artifact kept for 14 days:

  ```bash
  gh workflow run build-apk.yml
  gh run watch && gh run download --name 'ridebridge-*'   # then: adb install -r ridebridge-*.apk
  ```

  It uses the same key as releases, so it installs over any release and the next release installs over it.

### Versions

`.github/scripts/version.sh` computes both values from the latest `v*` tag reachable from the build and `n`, the number
of commits since that tag:

- version name: `0.2` on the tag itself, `0.2+5.abc1234` for 5 commits after `v0.2`;
- version code: `(major*10000 + minor*100 + patch) * 1000 + n`, e.g. `200000` for `v0.2`, `200005` for `v0.2` + 5 commits,
  `201000` for `v0.2.1`.

So builds between two releases always sort above the first and below the second. Tags must look like `vMAJOR[.MINOR[.PATCH]]`
with minor and patch at most 99, and fewer than 1000 commits between two tags, otherwise the script fails.

### One-time setup: signing key

Every release must be signed with the **same key**, otherwise Android refuses to update an installed app over a newer
build. Generate a keystore once and store it as repository secrets:

```bash
keytool -genkeypair -keystore ridebridge-release.jks -alias ridebridge \
        -keyalg RSA -keysize 2048 -validity 10000

# Settings → Secrets and variables → Actions, or with the GitHub CLI:
base64 -w0 ridebridge-release.jks | gh secret set KEYSTORE_BASE64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS            # ridebridge
gh secret set KEY_PASSWORD
```

Keep a backup of the `.jks` file and its passwords somewhere safe (never commit it): if you lose it, you can't publish
updates that install over existing ones. The release workflow fails rather than publish an unsigned APK when the
secrets are missing.
