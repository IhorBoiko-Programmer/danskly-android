#!/usr/bin/env bash
# Publishes build outputs to the `ci-output` branch, under one folder per kind:
#   scripts/publish-ci-output.sh <folder> <path>...
# The branch keeps only the latest outputs (one commit, force-pushed), so it never grows.
# Artifacts on the Actions page stay the main way to download them; this branch is a
# plain-git mirror for tools that cannot reach artifact storage.
set -euo pipefail
folder="$1"; shift
work="$(mktemp -d)"
remote="$(git config --get remote.origin.url)"

if git ls-remote --exit-code --heads origin ci-output >/dev/null 2>&1; then
  git clone --quiet --depth 1 --branch ci-output "$remote" "$work"
else
  git init --quiet -b ci-output "$work"
  git -C "$work" remote add origin "$remote"
fi
# Reuse the checkout's credentials.
auth="$(git config --get-regexp '^http\..*\.extraheader$' | head -1 || true)"
if [ -n "$auth" ]; then git -C "$work" config "${auth%% *}" "${auth#* }"; fi

rm -rf "${work:?}/$folder"
mkdir -p "$work/$folder"
for path in "$@"; do
  [ -e "$path" ] && cp -r "$path" "$work/$folder/"
done
{
  echo "commit: $GITHUB_SHA"
  echo "run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  date -u
} > "$work/$folder/BUILD_INFO.txt"

cd "$work"
git add -A
git -c user.name="github-actions" -c user.email="github-actions@users.noreply.github.com" \
  commit --quiet --allow-empty -m "CI output: $folder for $GITHUB_SHA"
# Squash to a single commit so the branch stays small.
git reset --quiet --soft "$(git commit-tree HEAD^{tree} -m "Latest CI output")"
for attempt in 1 2 3; do
  git push --quiet --force origin HEAD:ci-output && exit 0
  sleep $((attempt * 5))
  # Another job pushed meanwhile: rebuild on top of it.
  git fetch --quiet --depth 1 origin ci-output
  keep="$(mktemp -d)"; cp -r "$folder" "$keep/"
  git reset --quiet --hard FETCH_HEAD
  rm -rf "$folder"; cp -r "$keep/$folder" .
  git add -A
  git -c user.name="github-actions" -c user.email="github-actions@users.noreply.github.com" \
    commit --quiet --allow-empty -m "CI output: $folder"
  git reset --quiet --soft "$(git commit-tree HEAD^{tree} -m "Latest CI output")"
done
exit 1
