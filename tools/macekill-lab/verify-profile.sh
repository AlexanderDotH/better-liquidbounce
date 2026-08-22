#!/usr/bin/env bash
set -euo pipefail

lab_dir="$(cd "$(dirname "$0")" && pwd)"
profile="$lab_dir/profile.json"
observer="$lab_dir/observer-plugin/build/libs/macekill-lab-observer-0.1.0.jar"

expected_observer_sha="$(jq -er '.plugins[] | select(.name == "MaceKillLabObserver") | .sha256' "$profile")"
if [[ "$expected_observer_sha" == "UNBUILT" ]]; then
  echo "profile.json still has an UNBUILT observer hash" >&2
  exit 1
fi

actual_observer_sha="$(sha256sum "$observer" | awk '{print $1}')"
if [[ "$actual_observer_sha" != "$expected_observer_sha" ]]; then
  echo "observer SHA-256 mismatch: expected $expected_observer_sha, got $actual_observer_sha" >&2
  exit 1
fi

jq -e '
  .validation == "UNVALIDATED" and
  .minecraft.version == "26.2" and
  .minecraft.protocol == 776 and
  .paper.buildId == 112 and
  .paper.sha256 == "bd3a58cf96874e5ea6643f5f6fe9b4f5bf9e34b795fa078c2f0ee8b98b2f907e" and
  .java == 25 and
  .evidence.status == "NOT_RUN"
' "$profile" >/dev/null

echo "Pinned unvalidated lab profile and observer hash verified."
