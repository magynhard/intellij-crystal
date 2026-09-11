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
# Requirements: JDK-provisioning is done by Gradle; a RubyMine 2026.2 install
# (build 262+) whose bin/ contains rubymine.sh; a valid rubymine.key at
# RUBYMINE_KEY (default: ~/.config/JetBrains/RubyMine2026.2/rubymine.key). The
# key is copied with mode 0600 and removed at exit. Every run freshly indexes
# the target project plus the Crystal stdlib so persisted indexes cannot
# duplicate or hide findings.
#
# Notes:
# - The classic `inspect` command (rubymine.sh inspect <project> <profile>
#   <output>) is used on purpose: RubyMine 2026.2's Ruby-oriented `rinspect`
#   exits for kemal because no Gemfile exists, and its `--profile` option
#   rejects both file paths and names. The legacy command accepts the profile.
# - The generated profile explicitly disables non-Crystal inspections and
#   enables every Crystal inspection.
set -euo pipefail
umask 077

for required_command in cat chmod date dirname flock git grep install ln mkdir mv python3 readlink rm stat unzip; do
  if ! command -v "$required_command" >/dev/null; then
    echo "Required command is missing: $required_command" >&2
    exit 2
  fi
done

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
AUDIT_HOME_INPUT="${AUDIT_HOME:-/tmp/opencode/rm-audit}"
RUBYMINE_KEY="${RUBYMINE_KEY:-$HOME/.config/JetBrains/RubyMine2026.2/rubymine.key}"

if [[ ! -x "$RUBYMINE_HOME/bin/rubymine.sh" ]]; then
  echo "RubyMine launcher is not executable: $RUBYMINE_HOME/bin/rubymine.sh" >&2
  exit 2
fi
PRODUCT_INFO="$RUBYMINE_HOME/product-info.json"
if [[ ! -r "$PRODUCT_INFO" ]]; then
  echo "RubyMine product metadata is not readable: $PRODUCT_INFO" >&2
  exit 2
fi
RUBYMINE_BUILD="$(python3 - "$PRODUCT_INFO" <<'EOF'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    build_number = str(json.load(source).get("buildNumber", ""))
match = re.fullmatch(r"(?:[A-Z]+-)?([0-9]+)(?:\.[0-9]+)*", build_number)
if match is None:
    sys.exit(f"Cannot parse RubyMine buildNumber: {build_number!r}")
print(match.group(1))
EOF
)"
if (( RUBYMINE_BUILD < 262 )); then
  echo "RubyMine build 262 or newer is required, found: $RUBYMINE_BUILD" >&2
  exit 2
fi
if [[ ! -f "$RUBYMINE_KEY" || ! -r "$RUBYMINE_KEY" || -L "$RUBYMINE_KEY" ]]; then
  echo "RubyMine license key must be a readable regular file, not a symlink: $RUBYMINE_KEY" >&2
  exit 2
fi
RUBYMINE_KEY="$(readlink -f "$RUBYMINE_KEY")"
if [[ ! -x "$REPO_ROOT/gradlew" ]]; then
  echo "Gradle wrapper is not executable: $REPO_ROOT/gradlew" >&2
  exit 2
fi

if [[ -L "$AUDIT_HOME_INPUT" ]]; then
  echo "Refusing symlinked AUDIT_HOME: $AUDIT_HOME_INPUT" >&2
  exit 2
fi
AUDIT_HOME="$(readlink -m "$AUDIT_HOME_INPUT")"
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
case "$RUBYMINE_KEY" in
  "$AUDIT_HOME"|"$AUDIT_HOME/"*)
    echo "RUBYMINE_KEY must be outside AUDIT_HOME because audit state is replaced: $RUBYMINE_KEY" >&2
    exit 2
    ;;
esac

MARKER="$AUDIT_HOME/.crystal-inspect-audit"
MARKER_TEXT="Owned by scripts/crystal-inspect-audit.sh"
if [[ -e "$AUDIT_HOME" ]]; then
  if [[ -L "$AUDIT_HOME" || ! -d "$AUDIT_HOME" ]]; then
    echo "Refusing non-directory or symlinked AUDIT_HOME: $AUDIT_HOME" >&2
    exit 2
  fi
  if [[ "$(stat -c '%u' "$AUDIT_HOME")" != "$EUID" ]]; then
    echo "Refusing AUDIT_HOME not owned by the current user: $AUDIT_HOME" >&2
    exit 2
  fi
  if [[ ! -f "$MARKER" || -L "$MARKER" || "$(<"$MARKER")" != "$MARKER_TEXT" ]]; then
    echo "Refusing existing AUDIT_HOME without a valid ownership marker: $AUDIT_HOME" >&2
    exit 2
  fi
else
  install -d -m 700 "$AUDIT_HOME"
  printf '%s\n' "$MARKER_TEXT" > "$MARKER"
fi
chmod 700 "$AUDIT_HOME"
chmod 600 "$MARKER"

LOCK_PATH="$AUDIT_HOME/.lock"
if [[ -e "$LOCK_PATH" || -L "$LOCK_PATH" ]]; then
  if [[ ! -f "$LOCK_PATH" || -L "$LOCK_PATH" ]] ||
     [[ "$(stat -c '%u' "$LOCK_PATH")" != "$EUID" || "$(stat -c '%h' "$LOCK_PATH")" != 1 ]]; then
    echo "Refusing unsafe audit lock: $LOCK_PATH" >&2
    exit 2
  fi
fi

exec 9> "$LOCK_PATH"
if ! flock -n 9; then
  echo "Another audit is already using $AUDIT_HOME" >&2
  exit 2
fi

for retained_directory in "$AUDIT_HOME/logs" "$AUDIT_HOME/reports"; do
  if [[ -L "$retained_directory" || ( -e "$retained_directory" && ! -d "$retained_directory" ) ]]; then
    echo "Refusing unsafe retained audit directory: $retained_directory" >&2
    exit 2
  fi
  if [[ -d "$retained_directory" ]]; then
    chmod -R go-rwx "$retained_directory"
  fi
done

LICENSE_COPY=""
RUN_OUTPUT=""
OUTPUT_LINK=""
cleanup() {
  if [[ -n "$LICENSE_COPY" ]]; then
    rm -f -- "$LICENSE_COPY"
  fi
  if [[ -n "$RUN_OUTPUT" ]]; then
    rm -rf -- "$RUN_OUTPUT"
  fi
  if [[ -n "$OUTPUT_LINK" ]]; then
    rm -f -- "$OUTPUT_LINK"
  fi
}
trap cleanup EXIT

rm -rf -- "$AUDIT_HOME/config"

snapshot_target() {
  python3 - "$PROJECT_DIR" <<'EOF'
import hashlib
import os
import stat
import sys

root = os.fsencode(os.path.realpath(sys.argv[1]))
digest = hashlib.sha256()

def visit(path, relative, ancestors):
    link_metadata = os.lstat(path)
    digest.update(relative + b"\0")
    if stat.S_ISLNK(link_metadata.st_mode):
        target = os.path.realpath(path)
        if os.path.commonpath((root, target)) != root:
            sys.exit(f"Target symlink escapes project root: {os.fsdecode(relative)}")
        digest.update(b"L" + os.readlink(path) + b"\0")
        metadata = os.stat(path)
    else:
        metadata = link_metadata

    if stat.S_ISDIR(metadata.st_mode):
        identity = (metadata.st_dev, metadata.st_ino)
        if identity in ancestors:
            sys.exit(f"Target directory symlink cycle: {os.fsdecode(relative)}")
        next_ancestors = ancestors | {identity}
        with os.scandir(path) as entries:
            names = sorted(entry.name for entry in entries if entry.name != b".git")
        for name in names:
            child_relative = name if not relative else os.path.join(relative, name)
            visit(os.path.join(path, name), child_relative, next_ancestors)
    elif stat.S_ISREG(metadata.st_mode):
        digest.update(b"F")
        with open(path, "rb") as source:
            while chunk := source.read(1024 * 1024):
                digest.update(chunk)
    else:
        digest.update(f"S{metadata.st_mode:o}".encode() + b"\0")

visit(root, b"", set())
print(digest.hexdigest())
EOF
}

TARGET_REVISION="not-a-git-worktree"
GIT_WORKTREE="$(git -C "$PROJECT_DIR" rev-parse --is-inside-work-tree 2>/dev/null || true)"
if [[ "$GIT_WORKTREE" == "true" ]]; then
  if ! TARGET_REVISION="$(git -C "$PROJECT_DIR" rev-parse HEAD 2>/dev/null)"; then
    echo "The target Git worktree has no HEAD revision" >&2
    exit 2
  fi
  TARGET_STATUS="$(git -C "$PROJECT_DIR" status --porcelain=v1 --untracked-files=all)"
  echo "==> Target revision: $TARGET_REVISION"
  if [[ -n "$TARGET_STATUS" ]]; then
    echo "Refusing dirty target worktree; use a clean disposable checkout" >&2
    git -C "$PROJECT_DIR" status --short >&2
    exit 2
  fi
else
  if [[ "${ALLOW_NON_GIT_AUDIT_TARGET:-0}" != 1 ]]; then
    echo "Target is not a Git worktree; set ALLOW_NON_GIT_AUDIT_TARGET=1 to audit it explicitly" >&2
    exit 2
  fi
  echo "WARNING: auditing an explicitly allowed non-Git target" >&2
fi
echo "==> Hashing target contents"
TARGET_SNAPSHOT="$(snapshot_target)"

echo "==> Building dev plugin"
(cd "$REPO_ROOT" && ./gradlew buildPlugin -x buildSearchableOptions -q)

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
rm -rf -- "$AUDIT_HOME/plugins" "$AUDIT_HOME/system"
mkdir -p "$AUDIT_HOME"/{config,system,plugins,logs}
unzip -qo "$ZIP" -d "$AUDIT_HOME/plugins/"

# Copy only the license required for headless RubyMine, with no group/other
# access. The EXIT trap removes it after success, failure, or interruption.
LICENSE_COPY="$AUDIT_HOME/config/rubymine.key"
install -m 600 "$RUBYMINE_KEY" "$LICENSE_COPY"

echo "==> Writing base Crystal inspection profile"
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
    <inspection_tool class="CrystalParseError" enabled="true" level="ERROR" enabled_by_default="true" />
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

SCRATCH_PROJECT="$AUDIT_HOME/scratch"
SCRATCH_LOG_DIR="$AUDIT_HOME/logs/$RUN_ID-scratch"
rm -rf -- "$SCRATCH_PROJECT"
install -d -m 700 "$SCRATCH_PROJECT" "$SCRATCH_LOG_DIR"

rm -f -- "$AUDIT_HOME/rm-audit.vmoptions"
cat > "$AUDIT_HOME/rm-audit.vmoptions" <<EOF
-Didea.config.path=$AUDIT_HOME/config
-Didea.system.path=$AUDIT_HOME/system
-Didea.plugins.path=$AUDIT_HOME/plugins
-Didea.log.path=$SCRATCH_LOG_DIR
-Didea.trust.all.projects=true
EOF

# Phase A: enumerate every inspection tool the IDE knows. A scratch project
# (one tiny .cr file) is fast to index and produces .descriptions.xml, which
# lists every registered tool class. Default-enabled platform tools spin up
# their own index/heavy machinery (spell checker, RegExp host, javadoc...),
# produced dozens of noise findings at a prior run, crashers included, so the
# real run must only execute the Crystal tools.
printf 'x = Scratch::CONSTANT + 1\n' > "$SCRATCH_PROJECT/scratch.cr"
SCRATCH_OUTPUT="$AUDIT_HOME/scratch-out"
rm -rf -- "$SCRATCH_OUTPUT"
echo "==> Enumerating inspections on scratch project"
RUBYMINE_VM_OPTIONS="$AUDIT_HOME/rm-audit.vmoptions" \
  "$RUBYMINE_HOME/bin/rubymine.sh" inspect \
  "$SCRATCH_PROJECT" \
  "$AUDIT_HOME/crystal-profile.xml" \
  "$SCRATCH_OUTPUT" > /dev/null 2>&1 || true
if [[ ! -f "$SCRATCH_OUTPUT/.descriptions.xml" ]]; then
  echo "Inspection enumeration failed; no .descriptions.xml in $SCRATCH_OUTPUT" >&2
  echo "IDE log: $SCRATCH_LOG_DIR/idea.log" >&2
  exit 1
fi
rm -rf -- "$SCRATCH_PROJECT" "$SCRATCH_LOG_DIR"

echo "==> Generating Crystal-only profile"
CRITICAL_AUDIT_PROFILE="$AUDIT_HOME/crystal-audit.xml"
python3 - "$SCRATCH_OUTPUT/.descriptions.xml" "$CRITICAL_AUDIT_PROFILE" <<'EOF'
import os
import sys
import xml.etree.ElementTree as ET

desc_path, target_path = sys.argv[1], sys.argv[2]
tree = ET.parse(desc_path)
if tree.getroot().tag != "inspections":
    sys.exit(".descriptions.xml root must be <inspections>")

root = ET.Element("component", {"name": "InspectionProjectProfileManager"})
new_profile = ET.SubElement(root, "profile", {"version": "1.0"})
ET.SubElement(new_profile, "option", {"name": "myName", "value": "CrystalAudit"})
for inspection in tree.getroot().iter("inspection"):
    cls = inspection.get("shortName") or ""
    if cls.startswith("Crystal"):
        continue
    ET.SubElement(new_profile, "inspection_tool", {
        "class": cls,
        "enabled": "false",
        "level": inspection.get("defaultSeverity", "ERROR"),
        "enabled_by_default": "false",
    })
for cls, level in [
    ("CrystalArgumentCount", "WARNING"),
    ("CrystalTypeMismatch", "ERROR"),
    ("CrystalUnusedVariable", "WEAK WARNING"),
    ("CrystalEmptyCollection", "ERROR"),
    ("CrystalLibFunParameterType", "ERROR"),
    ("CrystalSingleQuoteString", "ERROR"),
    ("CrystalColonSpacing", "ERROR"),
    ("CrystalInstanceVarType", "ERROR"),
    ("CrystalParseError", "ERROR"),
    ("CrystalRequireContext", "ERROR"),
]:
    ET.SubElement(new_profile, "inspection_tool", {
        "class": cls,
        "enabled": "true",
        "level": level,
        "enabled_by_default": "true",
    })
ET.indent(root, space="  ")
ET.ElementTree(root).write(target_path, encoding="unicode", xml_declaration=False)
EOF
rm -rf -- "$SCRATCH_OUTPUT"

if [[ -e "$RUN_LOG_DIR" || -L "$RUN_LOG_DIR" ]] ||
   [[ -e "$RUN_OUTPUT" || -L "$RUN_OUTPUT" ]] ||
   [[ -e "$OUTPUT_LINK" || -L "$OUTPUT_LINK" ]]; then
  echo "Refusing pre-existing per-run audit path for $RUN_ID" >&2
  exit 2
fi
install -d -m 700 "$RUN_LOG_DIR" "$RUN_OUTPUT"

rm -f -- "$AUDIT_HOME/rm-audit-main.vmoptions"
cat > "$AUDIT_HOME/rm-audit-main.vmoptions" <<EOF
-Didea.config.path=$AUDIT_HOME/config
-Didea.system.path=$AUDIT_HOME/system
-Didea.plugins.path=$AUDIT_HOME/plugins
-Didea.log.path=$RUN_LOG_DIR
-Didea.trust.all.projects=true
EOF

echo "==> Running offline inspections on $PROJECT_DIR (fresh indexing, be patient)"
RUBYMINE_VM_OPTIONS="$AUDIT_HOME/rm-audit-main.vmoptions" \
  "$RUBYMINE_HOME/bin/rubymine.sh" inspect \
  "$PROJECT_DIR" \
  "$CRITICAL_AUDIT_PROFILE" \
  "$RUN_OUTPUT"

if ! grep -Fq "Loaded custom plugins: Crystal Language ($VERSION)" "$RUN_LOG_DIR/idea.log"; then
  echo "Crystal Language $VERSION was not loaded; inspect $RUN_LOG_DIR/idea.log" >&2
  exit 1
fi

TARGET_SNAPSHOT_AFTER="$(snapshot_target)"
if [[ "$TARGET_SNAPSHOT_AFTER" != "$TARGET_SNAPSHOT" ]]; then
  echo "The inspected project contents changed during the audit; refusing to publish results" >&2
  if [[ "$TARGET_REVISION" != "not-a-git-worktree" ]]; then
    git -C "$PROJECT_DIR" status --short >&2
  fi
  exit 1
fi
if [[ "$TARGET_REVISION" != "not-a-git-worktree" ]]; then
  TARGET_REVISION_AFTER="$(git -C "$PROJECT_DIR" rev-parse HEAD)"
  TARGET_STATUS_AFTER="$(git -C "$PROJECT_DIR" status --porcelain=v1 --untracked-files=all)"
  if [[ "$TARGET_REVISION_AFTER" != "$TARGET_REVISION" || -n "$TARGET_STATUS_AFTER" ]]; then
    echo "The inspected project changed during the audit; refusing to publish results" >&2
    git -C "$PROJECT_DIR" status --short >&2
    exit 1
  fi
fi

if [[ ! -d "$REPORTS_DIR" || -L "$REPORTS_DIR" || "$(stat -c '%u' "$REPORTS_DIR")" != "$EUID" ]]; then
  echo "Audit reports directory became unsafe during the run: $REPORTS_DIR" >&2
  exit 1
fi
if [[ ! -d "$RUN_OUTPUT" || -L "$RUN_OUTPUT" || "$(stat -c '%u' "$RUN_OUTPUT")" != "$EUID" ]]; then
  echo "Audit staging directory became unsafe during the run: $RUN_OUTPUT" >&2
  exit 1
fi

cat > "$RUN_OUTPUT/audit-metadata.txt" <<EOF
target=$PROJECT_DIR
targetRevision=$TARGET_REVISION
targetSha256=$TARGET_SNAPSHOT
pluginVersion=$VERSION
rubyMineBuild=$RUBYMINE_BUILD
EOF

FINAL_OUTPUT="$REPORTS_DIR/$RUN_ID"
if [[ -e "$FINAL_OUTPUT" || -L "$FINAL_OUTPUT" ]]; then
  echo "Refusing pre-existing final audit path: $FINAL_OUTPUT" >&2
  exit 1
fi
mv -T -- "$RUN_OUTPUT" "$FINAL_OUTPUT"
ln -s "reports/$RUN_ID" "$OUTPUT_LINK"
mv -Tf "$OUTPUT_LINK" "$AUDIT_HOME/out"

echo "==> Crystal results:"
python3 - "$AUDIT_HOME/out" <<'EOF'
import glob
import os
import sys
import xml.etree.ElementTree as ET

out_dir = sys.argv[1]
total = 0
noise = 0
for path in sorted(glob.glob(os.path.join(out_dir, "Crystal*.xml"))):
    name = os.path.basename(path)[:-4]
    print(f"--- {name}")
    for prob in ET.parse(path).iter("problem"):
        file_el = prob.find(".//file")
        line_el = prob.find(".//line")
        desc_el = prob.find(".//description")
        file_text = (file_el.text or "?") if file_el is not None else "?"
        file_text = file_text.replace("file://$PROJECT_DIR$/", "")
        file_text = file_text.replace("file://", "")
        line = line_el.text if line_el is not None else "?"
        desc = desc_el.text if desc_el is not None else "?"
        # Crystal inspections can fire inside injected fragments of foreign
        # files (markdown fences, heredoc-hosting scripts). Those findings are
        # not ours; keep only real .cr problems in the summary.
        if not file_text.endswith(".cr"):
            noise += 1
            continue
        total += 1
        print(f"  {file_text}:{line}: {desc}")
print(f"TOTAL: {total} Crystal problems in .cr files")
if noise:
    print(f"NOISE: {noise} Crystal findings in non-Crystal files (filtered)")
EOF
