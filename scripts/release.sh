#!/usr/bin/env bash
# Publishes a release of the Home on GitHub; the Home on the TV updates itself from it (Updates →
# "Apps instalados por APK"). Builds the release APK signed with this Mac's debug key, the key the
# Home was first installed with, so it must run on this Mac.
#
# Usage: scripts/release.sh 0.2.0 [--dry-run]
#   --dry-run  builds the APK and stops before committing, tagging, pushing or publishing.
set -euo pipefail

cd "$(dirname "$0")/.."

version="${1:-}"
dry_run="${2:-}"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: scripts/release.sh VERSION [--dry-run], with VERSION like 0.2.0" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Commit or stash your changes first: a release is built from what is committed." >&2
  exit 1
fi
if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Release from main." >&2
  exit 1
fi
if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
  echo "v$version already exists." >&2
  exit 1
fi
current="$(grep '^homeVersionName=' gradle.properties | cut -d= -f2)"
if [[ "$(printf '%s\n%s\n' "$current" "$version" | sort -V | tail -1)" != "$version" || "$current" == "$version" ]]; then
  echo "$version isn't newer than the current $current: the TV would never offer it." >&2
  exit 1
fi
if [[ "$dry_run" != "--dry-run" ]] && ! gh auth status >/dev/null 2>&1; then
  echo "Sign in to GitHub first: gh auth login" >&2
  exit 1
fi
# The build needs JDK 17 (Gradle 8.11 can't run on newer ones), whatever JAVA_HOME the shell has.
if command -v brew >/dev/null && [[ -d "$(brew --prefix openjdk@17 2>/dev/null)" ]]; then
  export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi

code="$(( $(grep '^homeVersionCode=' gradle.properties | cut -d= -f2) + 1 ))"
sed -i '' -e "s/^homeVersionName=.*/homeVersionName=$version/" -e "s/^homeVersionCode=.*/homeVersionCode=$code/" gradle.properties
trap 'git checkout --quiet gradle.properties' ERR

./gradlew --quiet :app:assembleRelease
# "tv" in the name is what the Home looks for in a release's files.
apk="build/free-my-tv-$version-tv.apk"
mkdir -p build
cp app/build/outputs/apk/release/app-release.apk "$apk"
echo "Built $apk ($version, versionCode $code)."

if [[ "$dry_run" == "--dry-run" ]]; then
  git checkout --quiet gradle.properties
  echo "Dry run: nothing committed, pushed or published."
  exit 0
fi

echo "This commits the version, tags v$version, pushes main and the tag to origin, and publishes the release on GitHub."
read -r -p "Type 'sim' to publish: " answer
if [[ "$answer" != "sim" ]]; then
  git checkout --quiet gradle.properties
  echo "Nothing published."
  exit 1
fi

git commit --quiet -am "chore: release $version"
git tag -a "v$version" -m "Free My TV $version"
git push origin main "v$version"
gh release create "v$version" "$apk" --title "Free My TV $version" --generate-notes
echo "Published v$version. On the TV: Atualizações → Free My TV → Atualizar para $version."
