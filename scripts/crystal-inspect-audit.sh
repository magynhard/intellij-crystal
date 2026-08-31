#!/usr/bin/env bash
# Headless Crystal inspection audit for external projects (kemal, shards, ...).
#
# Runs JetBrains' offline `inspect` command against a real IDE (default: the
# bundled RubyMine 2026.2) with the DEV build of this plugin installed into an
# ISOLATED config/system/plugins instance; the user's real IDE settings are
# never touched. Results are written per inspection; the summary prints all
# Crystal-* problems.
#
# Usage:
#   scripts/crystal-inspect-audit.sh /path/to/project
#   AUDIT_HOME=/tmp/audit RUBYMINE_HOME=/opt/jetbrains/RubyMine \
#     scripts/crystal-inspect-audit.sh /path/to/project
#
# Requirements: JDK-provisioning is done by gradle; a RubyMine 2026.2 install
# (build 262+) whose bin/ contains rubymine.sh; a valid rubymine.key in the
# user's real config (~/.config/JetBrains/RubyMine2026.2/); it is COPIED into
# the isolated config. Every run freshly indexes the target project plus the
# Crystal stdlib so persisted indexes cannot duplicate or hide findings.
#
# Notes:
# - The classic `inspect` command (rubymine.sh inspect <project> <profile>
#   <output>) is used on purpose: RubyMine 2026.2's Ruby-oriented `rinspect`
#   exits for kemal because no Gemfile exists, and its `--profile` option
#   rejects both file paths and names. The legacy command accepts the profile.
# - The profile enables every Crystal inspection. RubyMine may also run tools
#   enabled by default, but the summary reports only Crystal-* result files.
set -euo pipefail

PROJECT_DIR="${1:?usage: crystal-inspect-audit.sh <project-dir>}"
if [[ ! -d "$PROJECT_DIR" ]]; then
  echo "Project directory does not exist: $PROJECT_DIR" >&2
  exit 2
fi
PROJECT_DIR="$(readlink -f "$PROJECT_DIR")"
if [[ "$PROJECT_DIR" == "/" ]]; then
  echo "Refusing to inspect the filesystem root" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUBYMINE_HOME="${RUBYMINE_HOME:-/opt/jetbrains/RubyMine}"
AUDIT_HOME="${AUDIT_HOME:-/tmp/opencode/rm-audit}"

if [[ ! -x "$RUBYMINE_HOME/bin/rubymine.sh" ]]; then
  echo "RubyMine launcher is not executable: $RUBYMINE_HOME/bin/rubymine.sh" >&2
  exit 2
fi
if ! command -v flock >/dev/null; then
  echo "Required command is missing: flock must be available" >&2
  exit 2
fi

AUDIT_HOME="$(readlink -m "$AUDIT_HOME")"
case "$AUDIT_HOME" in
  ""|/|"$HOME"|"$REPO_ROOT"|"$PROJECT_DIR")
    echo "Refusing unsafe AUDIT_HOME: $AUDIT_HOME" >&2
    exit 2
    ;;
esac
case "$AUDIT_HOME/" in
  "$PROJECT_DIR/"*|"$REPO_ROOT/"*)
    echo "AUDIT_HOME must not be inside the inspected project or plugin repository: $AUDIT_HOME" >&2
    exit 2
    ;;
esac
case "$PROJECT_DIR/" in
  "$AUDIT_HOME/"*)
    echo "The inspected project must not be inside AUDIT_HOME: $PROJECT_DIR" >&2
    exit 2
    ;;
esac
case "$REPO_ROOT/" in
  "$AUDIT_HOME/"*)
    echo "The plugin repository must not be inside AUDIT_HOME: $REPO_ROOT" >&2
    exit 2
    ;;
esac

MARKER="$AUDIT_HOME/.crystal-inspect-audit"
MARKER_TEXT="Owned by scripts/crystal-inspect-audit.sh"
if [[ -e "$AUDIT_HOME" ]]; then
  if [[ ! -f "$MARKER" || -L "$MARKER" || "$(<"$MARKER")" != "$MARKER_TEXT" ]]; then
    echo "Refusing existing AUDIT_HOME without a valid ownership marker: $AUDIT_HOME" >&2
    exit 2
  fi
else
  mkdir -p "$AUDIT_HOME"
  printf '%s\n' "$MARKER_TEXT" > "$MARKER"
fi

if [[ -L "$AUDIT_HOME/.lock" ]]; then
  echo "Refusing symlinked audit lock: $AUDIT_HOME/.lock" >&2
  exit 2
fi

exec 9> "$AUDIT_HOME/.lock"
if ! flock -n 9; then
  echo "Another audit is already using $AUDIT_HOME" >&2
  exit 2
fi

echo "==> Building dev plugin"
(cd "$REPO_ROOT" && ./gradlew buildPlugin -q)

VERSION=""
while IFS='=' read -r key value; do
  key="${key//[[:space:]]/}"
  if [[ "$key" == "version" ]]; then
    VERSION="${value//[[:space:]]/}"
    break
  fi
done < "$REPO_ROOT/gradle.properties"
ZIP="$REPO_ROOT/build/distributions/intellij-crystal-$VERSION.zip"
if [[ -z "$VERSION" || ! -f "$ZIP" ]]; then
  echo "Expected plugin distribution not found: $ZIP" >&2
  exit 1
fi

echo "==> Installing plugin into isolated instance at $AUDIT_HOME"
for owned_directory in "$AUDIT_HOME/logs" "$AUDIT_HOME/reports"; do
  if [[ -L "$owned_directory" || ( -e "$owned_directory" && ! -d "$owned_directory" ) ]]; then
    echo "Refusing unsafe audit directory: $owned_directory" >&2
    exit 2
  fi
done
rm -rf -- "$AUDIT_HOME/config" "$AUDIT_HOME/plugins" "$AUDIT_HOME/system"
mkdir -p "$AUDIT_HOME"/{config,system,plugins,logs}
unzip -qo "$ZIP" -d "$AUDIT_HOME/plugins/"

# License: copy the user's key into the isolated config so headless mode can
# start a licensed RubyMine. Nothing else is taken from the real config.
USER_KEY="$HOME/.config/JetBrains/RubyMine2026.2/rubymine.key"
if [ -f "$USER_KEY" ]; then
  cp "$USER_KEY" "$AUDIT_HOME/config/"
fi

echo "==> Writing Crystal inspection profile"
rm -f -- "$AUDIT_HOME/crystal-profile.xml"
cat > "$AUDIT_HOME/crystal-profile.xml" <<'EOF'
<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="CrystalAudit" />
    <inspection_tool class="CrystalArgumentCount" enabled="true" level="WARNING" enabled_by_default="true" />
    <inspection_tool class="CrystalTypeMismatch" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalUnusedVariable" enabled="true" level="WEAK WARNING" enabled_by_default="true" />
    <inspection_tool class="CrystalEmptyCollection" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalLibFunParameterType" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalSingleQuoteString" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalColonSpacing" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalInstanceVarType" enabled="true" level="ERROR" enabled_by_default="true" />
    <inspection_tool class="CrystalRequireContext" enabled="true" level="ERROR" enabled_by_default="true" />
  </profile>
</component>
EOF

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-$$"
RUN_LOG_DIR="$AUDIT_HOME/logs/$RUN_ID"
REPORTS_DIR="$AUDIT_HOME/reports"
RUN_OUTPUT="$REPORTS_DIR/$RUN_ID.partial"
OUTPUT_LINK="$AUDIT_HOME/.out.$RUN_ID"
if [[ -e "$AUDIT_HOME/out" && ! -L "$AUDIT_HOME/out" ]]; then
  echo "Refusing legacy non-symlink report path: $AUDIT_HOME/out" >&2
  exit 2
fi
if [[ -e "$RUN_LOG_DIR" || -L "$RUN_LOG_DIR" || -e "$RUN_OUTPUT" || -L "$RUN_OUTPUT" ]]; then
  echo "Refusing pre-existing per-run audit path for $RUN_ID" >&2
  exit 2
fi
mkdir -p "$RUN_LOG_DIR" "$RUN_OUTPUT"
cleanup() {
  rm -rf -- "$RUN_OUTPUT" "$OUTPUT_LINK"
}
trap cleanup EXIT

rm -f -- "$AUDIT_HOME/rm-audit.vmoptions"
cat > "$AUDIT_HOME/rm-audit.vmoptions" <<EOF
-Didea.config.path=$AUDIT_HOME/config
-Didea.system.path=$AUDIT_HOME/system
-Didea.plugins.path=$AUDIT_HOME/plugins
-Didea.log.path=$RUN_LOG_DIR
-Didea.trust.all.projects=true
EOF

echo "==> Running offline inspections on $PROJECT_DIR (fresh indexing, be patient)"
RUBYMINE_VM_OPTIONS="$AUDIT_HOME/rm-audit.vmoptions" \
  "$RUBYMINE_HOME/bin/rubymine.sh" inspect \
  "$PROJECT_DIR" \
  "$AUDIT_HOME/crystal-profile.xml" \
  "$RUN_OUTPUT"

if ! grep -Fq "Loaded custom plugins: Crystal Language ($VERSION)" "$RUN_LOG_DIR/idea.log"; then
  echo "Crystal Language $VERSION was not loaded; inspect $RUN_LOG_DIR/idea.log" >&2
  exit 1
fi

FINAL_OUTPUT="$REPORTS_DIR/$RUN_ID"
mv "$RUN_OUTPUT" "$FINAL_OUTPUT"
ln -s "reports/$RUN_ID" "$OUTPUT_LINK"
mv -Tf "$OUTPUT_LINK" "$AUDIT_HOME/out"
trap - EXIT

echo "==> Crystal results:"
python3 - "$AUDIT_HOME/out" <<'EOF'
import glob
import os
import sys
import xml.etree.ElementTree as ET

out_dir = sys.argv[1]
total = 0
for path in sorted(glob.glob(os.path.join(out_dir, "Crystal*.xml"))):
    name = os.path.basename(path)[:-4]
    problems = list(ET.parse(path).iter("problem"))
    print(f"--- {name}: {len(problems)}")
    for prob in problems:
        file_el = prob.find(".//file")
        line_el = prob.find(".//line")
        desc_el = prob.find(".//description")
        file_text = (file_el.text or "?") if file_el is not None else "?"
        file_text = file_text.replace("file://$PROJECT_DIR$/", "")
        line = line_el.text if line_el is not None else "?"
        desc = desc_el.text if desc_el is not None else "?"
        print(f"  {file_text}:{line}: {desc}")
    total += len(problems)
print(f"TOTAL: {total} Crystal problems")
EOF
