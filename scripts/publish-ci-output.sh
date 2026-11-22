#!/usr/bin/env bash
# Publishes build outputs to their own branch, `ci-<name>` (ci-tests, ci-apk):
#   scripts/publish-ci-output.sh <name> <path>...
# Each branch holds one commit with the latest outputs (force-pushed), so it never grows
# and two jobs never overwrite each other. Artifacts on the Actions page stay the main
# way to download; these branches are a plain-git mirror for tools that cannot reach
# artifact storage.
set -euo pipefail
export GIT_AUTHOR_NAME="github-actions" GIT_AUTHOR_EMAIL="github-actions@users.noreply.github.com"
export GIT_COMMITTER_NAME="$GIT_AUTHOR_NAME" GIT_COMMITTER_EMAIL="$GIT_AUTHOR_EMAIL"

name="$1"; shift
branch="ci-$name"
work="$(mktemp -d)"
remote="$(git config --get remote.origin.url)"
# Reuse the credentials actions/checkout stored for this repository.
auth_key="$(git config --name-only --get-regexp '^http\..*\.extraheader$' | head -1 || true)"

git init --quiet -b "$branch" "$work"
for path in "$@"; do
  [ -e "$path" ] && cp -r "$path" "$work/"
done
{
  echo "commit: $GITHUB_SHA"
  echo "run: $GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
  date -u
} > "$work/BUILD_INFO.txt"

cd "$work"
git add -A
git commit --quiet -m "Latest CI output ($name) for $GITHUB_SHA"
if [ -n "$auth_key" ]; then
  git -c "$auth_key=$(git -C "$GITHUB_WORKSPACE" config --get "$auth_key")" push --quiet --force "$remote" "HEAD:$branch"
else
  git push --quiet --force "$remote" "HEAD:$branch"
fi
