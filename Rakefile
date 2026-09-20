
require 'dotenv/load'
require 'tmpdir'

STDLIB_AUDIT_VERSION = "1.21.0"
STDLIB_AUDIT_CORPORA = %w[indexed distribution].freeze

# Resolve the Crystal stdlib source root for the parse audits. An explicit
# CRYSTAL_STDLIB_ROOT wins; otherwise the first absolute CRYSTAL_PATH entry
# that carries a VERSION file (the installed distribution root).
def resolve_stdlib_root
  override = ENV['CRYSTAL_STDLIB_ROOT']
  return override unless override.nil? || override.empty?

  crystal_path = `crystal env CRYSTAL_PATH 2>/dev/null`.strip
  abort "ERROR: 'crystal' is not on PATH and CRYSTAL_STDLIB_ROOT is not set" if crystal_path.empty?

  root = crystal_path.split(':').reject(&:empty?).find do |entry|
    File.absolute_path?(entry) && File.file?(File.join(entry, 'VERSION'))
  end
  abort "ERROR: no absolute Crystal stdlib root with a VERSION file in CRYSTAL_PATH=#{crystal_path}" if root.nil?

  root
end

def stdlib_audit_root
  root = resolve_stdlib_root
  version_file = File.join(root, 'VERSION')
  abort "ERROR: Crystal stdlib root has no VERSION file: #{root}" unless File.file?(version_file)

  version = File.read(version_file).strip
  unless version == STDLIB_AUDIT_VERSION
    abort "ERROR: Crystal #{STDLIB_AUDIT_VERSION} is required, found '#{version}' at #{root} " \
          "(set CRYSTAL_STDLIB_ROOT to a #{STDLIB_AUDIT_VERSION} source root)"
  end

  root
end

#
# Create a crystal test project and create a .env file in this project
# and add the path inside, e.g.:
# TEST_APP_PATH=/home/myuser/projects/my_project
#
desc "Run plugin in uncached, sandboxed IntelliJ environment"
task :run do |t|
  unless File.exist? ".env"
    path = Dir.pwd
    ENV['TEST_APP_PATH'] = path
    File.write ".env", "TEST_APP_PATH=#{path}"
  end
  system("./gradlew", "cleanSandbox", "runIde", "--args=#{ENV['TEST_APP_PATH']}")
end

desc "Bump patch version in gradle.properties and README.md"
task :bump_version do |t|
  props_path = "gradle.properties"
  readme_path = "README.md"

  # Read current version from gradle.properties
  props = File.read(props_path)
  version_match = props.match(/^version\s*=\s*(\d+\.\d+\.\d+)/)
  unless version_match
    abort "ERROR: Could not parse version from #{props_path}"
  end

  old_version = version_match[1]
  parts = old_version.split(".").map(&:to_i)
  parts[2] += 1
  new_version = parts.join(".")

  # Update gradle.properties
  File.write(props_path, props.sub(old_version, new_version))

  # Update README.md (badge URL)
  readme = File.read(readme_path)
  File.write(readme_path, readme.gsub("Plugin-v#{old_version}", "Plugin-v#{new_version}"))

  puts "Version bumped: #{old_version} -> #{new_version}"
end

desc "Build plugin and store it in build/distributions"
task :build do |t|
  system("./gradlew", "buildPlugin")
end

desc "Run the pinned Crystal #{STDLIB_AUDIT_VERSION} stdlib parse audits (indexed + distribution)"
task :stdlib_parse_audit do
  if ENV['SKIP_STDLIB_AUDIT'] == '1'
    puts "SKIP_STDLIB_AUDIT=1 — skipping stdlib parse audits"
    next
  end

  root = stdlib_audit_root
  STDLIB_AUDIT_CORPORA.each do |corpus|
    puts "Stdlib parse audit (#{corpus}) against #{root}..."
    system("./gradlew", "stdlibParseAudit", "-PcrystalCorpus=#{corpus}", "-PcrystalStdlibRoot=#{root}") or
      abort "ERROR: stdlib parse audit (#{corpus}) failed — see build/reports/stdlib-parse-audit/#{corpus}/report.txt"
  end
end

desc "Run the full Gradle test suite"
task :test do
  system("./gradlew", "test") or abort "ERROR: test suite failed"
end

desc "Full release: run the stdlib parse audits and tests, bump version, build, tag, push, and create GitHub release"
task :release => [:stdlib_parse_audit, :test, :bump_version] do |t|
  # Read new version
  props = File.read("gradle.properties")
  version = props.match(/^version\s*=\s*(\S+)/)[1]
  tag = "v#{version}"
  puts "Releasing #{tag}..."

  # Build plugin
  system("./gradlew", "buildPlugin") or abort "Build failed"

  # Extract changelog from plugin.xml <changeNotes> if present
  plugin_xml = File.read("src/main/resources/META-INF/plugin.xml")
  changelog_match = plugin_xml.match(/<changeNotes><!\[CDATA\[(.*?)\]\]><\/changeNotes>/m)
  changelog = changelog_match ? changelog_match[1].strip : nil

  # Find built plugin ZIP
  zip = Dir["build/distributions/intellij-crystal-#{version}.zip"].first
  abort "Plugin ZIP not found for version #{version}" unless zip

  # Git: commit, tag, push
  system("git", "add", "gradle.properties", "README.md")
  system("git", "commit", "-m", "chore(release): #{tag}")
  system("git", "tag", tag)
  system("git", "push", "origin", "master", "--tags") or abort "Push failed"

  # GitHub release
  if changelog && !changelog.empty?
    puts "Using changelog from plugin.xml as release notes"
    # Write changelog to temp file for gh release create
    release_notes = File.join(Dir.tmpdir, "release_notes_#{version}.md")
    File.write(release_notes, changelog)
    system("gh", "release", "create", tag, zip, "--title", tag, "--notes-file", release_notes)
    File.delete(release_notes)
  else
    puts "No changelog in plugin.xml, using auto-generated notes"
    system("gh", "release", "create", tag, zip, "--title", tag, "--generate-notes")
  end

  abort "GitHub release failed" unless $?.success?
  puts "Release #{tag} published: https://github.com/magynhard/intellij-crystal/releases/tag/#{tag}"
end
