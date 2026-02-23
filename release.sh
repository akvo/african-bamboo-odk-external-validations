#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
# release.sh — Build, version bump, and publish
# ─────────────────────────────────────────────
# Usage:
#   ./release.sh              # auto-bump patch (1.1 → 1.2)
#   ./release.sh minor        # bump minor      (1.1 → 1.2)
#   ./release.sh major        # bump major      (1.1 → 2.0)
#   ./release.sh patch        # bump patch      (1.1.0 → 1.1.1)
#   ./release.sh 2.5          # set exact version
#   ./release.sh --update     # replace APK on latest existing tag (no version bump)
# ─────────────────────────────────────────────

REPO="akvo/african-bamboo-odk-external-validations"
GRADLE_FILE="app/build.gradle.kts"
APK_PATH="app/build/outputs/apk/release/app-release.apk"

# ── Colors ──
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}▸${NC} $1"; }
ok()    { echo -e "${GREEN}✔${NC} $1"; }
warn()  { echo -e "${YELLOW}⚠${NC} $1"; }
fail()  { echo -e "${RED}✖ $1${NC}"; exit 1; }

# ── Pre-flight checks ──
command -v gh >/dev/null 2>&1       || fail "gh CLI not found. Install: https://cli.github.com"
command -v ./gradlew >/dev/null 2>&1 || fail "Run this script from the project root"
[ -f "$GRADLE_FILE" ]               || fail "$GRADLE_FILE not found"
[ -f "keystore.properties" ]        || fail "keystore.properties not found. See README for setup."

# ── Read current version ──
CURRENT_CODE=$(grep 'versionCode' "$GRADLE_FILE" | head -1 | sed 's/[^0-9]//g')
CURRENT_NAME=$(grep 'versionName' "$GRADLE_FILE" | head -1 | sed 's/.*"\(.*\)".*/\1/')
info "Current version: v${CURRENT_NAME} (code ${CURRENT_CODE})"

# ── Handle --update mode (replace APK on existing release) ──
if [[ "${1:-}" == "--update" ]]; then
    TAG="v${CURRENT_NAME}"
    info "Update mode: rebuilding and replacing APK on ${TAG}"

    info "Checking for uncommitted changes..."
    if ! git diff --quiet || ! git diff --cached --quiet; then
        fail "Uncommitted changes found. Commit or stash first."
    fi

    info "Building release APK..."
    ./gradlew clean assembleRelease --quiet
    ok "Build complete"

    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    info "APK size: ${APK_SIZE}"

    # Check if release exists
    if ! gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
        fail "Release ${TAG} not found on GitHub"
    fi

    # Remove old APK and upload new one
    info "Replacing APK on release ${TAG}..."
    gh release delete-asset "$TAG" app-release.apk --repo "$REPO" --yes 2>/dev/null || true
    gh release upload "$TAG" "$APK_PATH" --repo "$REPO"
    ok "Release ${TAG} updated with new APK (${APK_SIZE})"

    RELEASE_URL="https://github.com/${REPO}/releases/tag/${TAG}"
    echo ""
    ok "Done! ${RELEASE_URL}"
    exit 0
fi

# ── Calculate new version ──
BUMP="${1:-minor}"

IFS='.' read -r MAJOR MINOR PATCH <<< "${CURRENT_NAME}.0"
PATCH="${PATCH:-0}"

case "$BUMP" in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        NEW_NAME="${MAJOR}.${MINOR}"
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        NEW_NAME="${MAJOR}.${MINOR}"
        ;;
    patch)
        PATCH=$((PATCH + 1))
        NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
        ;;
    *)
        # Exact version provided
        NEW_NAME="$BUMP"
        ;;
esac

NEW_CODE=$((CURRENT_CODE + 1))
TAG="v${NEW_NAME}"

echo ""
info "Version bump: v${CURRENT_NAME} (${CURRENT_CODE}) → v${NEW_NAME} (${NEW_CODE})"
echo ""

# ── Confirm ──
read -rp "Proceed with release ${TAG}? [y/N] " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || { info "Aborted."; exit 0; }
echo ""

# ── Check clean working tree ──
if ! git diff --quiet || ! git diff --cached --quiet; then
    warn "Uncommitted changes detected:"
    git status --short
    echo ""
    read -rp "Commit these changes before release? [y/N] " commit_first
    if [[ "$commit_first" =~ ^[Yy]$ ]]; then
        git add -A
        git commit -m "chore: pre-release cleanup"
        ok "Changes committed"
    else
        fail "Commit or stash changes first."
    fi
fi

# ── Update version in build.gradle.kts ──
info "Updating version in ${GRADLE_FILE}..."
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" "$GRADLE_FILE"
sed -i "s/versionName = \"${CURRENT_NAME}\"/versionName = \"${NEW_NAME}\"/" "$GRADLE_FILE"
ok "Version updated to ${NEW_NAME} (code ${NEW_CODE})"

# ── Build ──
info "Building release APK..."
./gradlew clean assembleRelease --quiet
ok "Build complete"

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
info "APK: ${APK_PATH} (${APK_SIZE})"

# ── Verify signing ──
info "Verifying APK signature..."
APKSIGNER=$(find "$HOME/Android/Sdk/build-tools" -name "apksigner" 2>/dev/null | sort -V | tail -1)
if [ -n "$APKSIGNER" ]; then
    SIGNER=$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>&1 | head -1)
    ok "Signed: ${SIGNER}"
else
    warn "apksigner not found — skipping verification"
fi

# ── Generate release notes ──
info "Generating release notes..."
PREV_TAG=$(git tag --sort=-v:refname | head -1)

if [ -n "$PREV_TAG" ]; then
    CHANGES=$(git log "${PREV_TAG}..HEAD" --oneline --no-merges | grep -v "^.*chore: bd sync" | grep -v "^.*chore: pre-release" || true)
    COMPARE_URL="https://github.com/${REPO}/compare/${PREV_TAG}...${TAG}"
else
    CHANGES=$(git log --oneline --no-merges | grep -v "^.*chore: bd sync" || true)
    COMPARE_URL=""
fi

# Build notes body
NOTES="## What's Changed in ${TAG}\n\n"
if [ -n "$CHANGES" ]; then
    while IFS= read -r line; do
        HASH=$(echo "$line" | cut -d' ' -f1)
        MSG=$(echo "$line" | cut -d' ' -f2-)
        NOTES+="- ${MSG} (\`${HASH}\`)\n"
    done <<< "$CHANGES"
else
    NOTES+="- Maintenance release\n"
fi

NOTES+="\n### Details\n"
NOTES+="- **Version**: ${NEW_NAME} (code ${NEW_CODE})\n"
NOTES+="- **Min SDK**: 24 (Android 7.0)\n"
NOTES+="- **APK size**: ${APK_SIZE}\n"

if [ -n "$COMPARE_URL" ]; then
    NOTES+="\n**Full changelog**: ${COMPARE_URL}\n"
fi

NOTES+="\n### Assets\n"
NOTES+="- \`app-release.apk\` — Signed release APK\n"

echo ""
echo -e "$NOTES"
read -rp "Publish with these notes? [y/N] " confirm_notes
[[ "$confirm_notes" =~ ^[Yy]$ ]] || { info "Aborted. Version already bumped — revert with git checkout $GRADLE_FILE"; exit 0; }

# ── Commit, tag, push ──
info "Committing version bump..."
git add "$GRADLE_FILE"
git commit -m "release: v${NEW_NAME}"
ok "Committed"

info "Creating tag ${TAG}..."
git tag "$TAG"
ok "Tag created"

info "Pushing to remote..."
git push origin main
git push origin "$TAG"
ok "Pushed"

# ── Create GitHub release ──
info "Creating GitHub release..."
NOTES_PLAIN=$(echo -e "$NOTES")
gh release create "$TAG" "$APK_PATH" \
    --repo "$REPO" \
    --title "${TAG}" \
    --notes "$NOTES_PLAIN"

RELEASE_URL="https://github.com/${REPO}/releases/tag/${TAG}"
ok "Release published!"

echo ""
echo -e "${GREEN}════════════════════════════════════════${NC}"
echo -e "${GREEN}  Release ${TAG} published successfully${NC}"
echo -e "${GREEN}  ${RELEASE_URL}${NC}"
echo -e "${GREEN}════════════════════════════════════════${NC}"
