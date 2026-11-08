#!/usr/bin/env bash
# Prints versionName and versionCode for HEAD as KEY=VALUE lines (suitable for $GITHUB_OUTPUT).
#
# Based on the latest "v*" tag reachable from HEAD plus the number of commits since it:
#   versionName = <tag without "v">            on the tag itself, e.g. 0.2
#                 <tag without "v">+<n>.<sha>  n commits after it,  e.g. 0.2+5.abc1234
#   versionCode = (major*10000 + minor*100 + patch) * 1000 + n
# so every build on top of v0.2 sorts above the v0.2 release and below v0.3 (as long as n < 1000).
# Needs the full history and tags (actions/checkout with fetch-depth: 0).
set -euo pipefail

if desc=$(git describe --tags --long --match 'v*' 2>/dev/null); then
  sha=${desc##*-g}
  rest=${desc%-g*}
  count=${rest##*-}
  tag=${rest%-*}
else
  tag=v0.0.0
  count=$(git rev-list --count HEAD)
  sha=$(git rev-parse --short HEAD)
fi

IFS=. read -r major minor patch _ <<< "${tag#v}"
major=${major:-0}; minor=${minor:-0}; patch=${patch:-0}
for n in "$major" "$minor" "$patch"; do
  [[ $n =~ ^[0-9]+$ ]] || { echo "Cannot parse tag '$tag' as vMAJOR[.MINOR[.PATCH]]" >&2; exit 1; }
done
if (( minor > 99 || patch > 99 || count > 999 )); then
  echo "Out of range for the versionCode scheme: minor/patch <= 99, commits since tag <= 999" >&2
  exit 1
fi

name=${tag#v}
(( count == 0 )) || name="$name+$count.$sha"
echo "versionName=$name"
echo "versionCode=$(( (major * 10000 + minor * 100 + patch) * 1000 + count ))"
