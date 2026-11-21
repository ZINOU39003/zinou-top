#!/usr/bin/env bash
# ZINOU TOP - bring SmartTube upstream changes into the 'zinou' branch, safely and reviewably.
#
#   scripts/upstream/sync-upstream.sh [upstream-ref]      (default: smarttube/master)
#
# What it does:
#   1. refuses to run with uncommitted changes (nothing of yours is ever overwritten)
#   2. fetches SmartTube into the remote 'smarttube' and fast-forwards the local 'upstream' branch
#      (never rewritten: if upstream history was rewritten, the script stops)
#   3. prints a review report: new commits, files touched by both sides, sensitive changes
#      (build files, manifests, workflows, update/network code, binaries, submodule moves)
#   4. creates the branch merge/upstream-<sha> from 'zinou' and runs
#      `git merge --no-ff --no-commit upstream`
#        - conflicts: lists each conflicted file with the reason, and STOPS (no blind resolution)
#        - no conflicts: sets upstream.commit in zinou.properties and leaves the merge staged
#          for review/build/test; you commit it yourself
# No force-push, no rebase, no reset of existing branches. Works in Git Bash on Windows.
set -euo pipefail

UPSTREAM_URL="https://github.com/yuliskov/SmartTube.git"
REMOTE="smarttube"
DEV_BRANCH="zinou"
MIRROR_BRANCH="upstream"
REF="${1:-$REMOTE/master}"

die() { echo "ERROR: $*" >&2; exit 1; }
say() { printf '\n== %s ==\n' "$*"; }

cd "$(git rev-parse --show-toplevel)"

[ -z "$(git status --porcelain --untracked-files=no)" ] || die "Uncommitted changes. Commit or stash them first."
git show-ref --verify --quiet "refs/heads/$DEV_BRANCH" || die "Branch '$DEV_BRANCH' not found."
git show-ref --verify --quiet "refs/heads/$MIRROR_BRANCH" || die "Branch '$MIRROR_BRANCH' not found (see UPSTREAM.md)."
[ "$(git rev-parse --abbrev-ref HEAD)" != "$MIRROR_BRANCH" ] || die "Do not run this while on '$MIRROR_BRANCH'."

# Keep ZINOU versions of files listed with merge=ours in .gitattributes (README, issue templates).
git config merge.ours.driver true

if ! git remote get-url "$REMOTE" >/dev/null 2>&1; then
    git remote add "$REMOTE" "$UPSTREAM_URL"
fi

say "Fetching SmartTube upstream"
git fetch --tags "$REMOTE"
NEW=$(git rev-parse --verify "$REF^{commit}") || die "Unknown upstream ref '$REF'."
OLD=$(git rev-parse "$MIRROR_BRANCH")

if [ "$NEW" = "$OLD" ]; then
    echo "Nothing to do: '$MIRROR_BRANCH' is already at $(git rev-parse --short "$NEW")."
    exit 0
fi
git merge-base --is-ancestor "$OLD" "$NEW" || die "Upstream history was rewritten (not a fast-forward). Review manually; nothing changed."
git branch -f "$MIRROR_BRANCH" "$NEW"
SHORT=$(git rev-parse --short=7 "$NEW")

say "New upstream commits ($(git rev-list --count "$OLD..$NEW"))"
git log --no-merges --format='  %h %ad %s' --date=short "$OLD..$NEW" | head -n 100

UP_FILES=$(git diff --name-only "$OLD" "$NEW")
ZINOU_FILES=$(git diff --name-only "$OLD" "$DEV_BRANCH")

say "Files changed by BOTH upstream and ZINOU (review carefully)"
comm -12 <(printf '%s\n' "$UP_FILES" | sort) <(printf '%s\n' "$ZINOU_FILES" | sort) | sed 's/^/  /' || true

say "Sensitive upstream changes (security review)"
printf '%s\n' "$UP_FILES" | grep -E '(\.gradle|AndroidManifest\.xml|^\.github/|appupdatechecker|[Uu]pdate|ConstantsApi|proguard|\.(jar|so|aar|apk|jks|keystore)$|gradle-wrapper)' | sed 's/^/  /' || echo "  none"
for sm in SharedModules MediaServiceCore; do
    a=$(git ls-tree "$OLD" "$sm" | awk '{print $3}')
    b=$(git ls-tree "$NEW" "$sm" | awk '{print $3}')
    if [ -n "$a" ] && [ "$a" != "$b" ]; then
        echo "  submodule $sm moved: ${a:0:9} -> ${b:0:9}  (review: https://github.com/yuliskov/$sm/compare/$a...$b)"
    fi
done

REVIEW="merge/upstream-$SHORT"
say "Merging into review branch '$REVIEW'"
git show-ref --verify --quiet "refs/heads/$REVIEW" && die "Branch '$REVIEW' already exists."
git switch -c "$REVIEW" "$DEV_BRANCH"

if git merge --no-ff --no-commit "$MIRROR_BRANCH"; then
    sed -i.bak -E "s/^upstream\.commit=.*/upstream.commit=$SHORT/" zinou.properties && rm -f zinou.properties.bak
    git add zinou.properties
    git submodule update --init --recursive
    say "Merge prepared (NOT committed)"
    git diff --cached --stat | tail -n 25
    cat <<EOF

Next steps:
  1. Review:   git diff --cached
  2. Build:    ./gradlew clean assembleZinouDebug   (and test on a device)
  3. Commit:   git commit -m "Merge SmartTube upstream $SHORT"
  4. Push the branch and open a pull request into '$DEV_BRANCH' (CI must pass).
  To abandon:  git merge --abort && git switch $DEV_BRANCH && git branch -D $REVIEW
EOF
else
    say "CONFLICTS - nothing was resolved automatically"
    deleted_by_zinou=$(git diff --name-only --diff-filter=D "$OLD" "$DEV_BRANCH" || true)
    git diff --name-only --diff-filter=U | while read -r f; do
        if printf '%s\n' "$deleted_by_zinou" | grep -qx "$f"; then
            why="removed by ZINOU (upstream-only GitHub file). Usually: git rm \"$f\""
        elif printf '%s\n' "$ZINOU_FILES" | grep -qx "$f"; then
            why="modified by both: keep upstream's change AND re-apply the ZINOU lines (marked 'ZINOU TOP')"
        else
            why="upstream conflict"
        fi
        echo "  $f"
        echo "      -> $why"
    done
    cat <<EOF

Resolve each file by hand, then:
  git add <files> && set upstream.commit=$SHORT in zinou.properties && git add zinou.properties
  git submodule update --init --recursive && ./gradlew clean assembleZinouDebug
  git commit
To abandon: git merge --abort && git switch $DEV_BRANCH && git branch -D $REVIEW
EOF
    exit 2
fi
