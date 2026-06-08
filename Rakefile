
require 'dotenv/load'

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
  system %Q(./gradlew cleanSandbox runIde --args="#{ENV['TEST_APP_PATH']}")
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
  system "./gradlew buildPlugin"
end

desc "Full release: bump version, build, tag, push, and create GitHub release"
task :release => :bump_version do |t|
  # Read new version
  props = File.read("gradle.properties")
  version = props.match(/^version\s*=\s*(\S+)/)[1]
  tag = "v#{version}"
  puts "Releasing #{tag}..."

  # Build plugin
  system "./gradlew buildPlugin" or abort "Build failed"

  # Extract changelog from plugin.xml <changeNotes> if present
  plugin_xml = File.read("src/main/resources/META-INF/plugin.xml")
  changelog_match = plugin_xml.match(/<changeNotes><!\[CDATA\[(.*?)\]\]><\/changeNotes>/m)
  changelog = changelog_match ? changelog_match[1].strip : nil

  # Find built plugin ZIP
  zip = Dir["build/distributions/intellij-crystal-#{version}.zip"].first
  abort "Plugin ZIP not found for version #{version}" unless zip

  # Git: commit, tag, push
  system "git add gradle.properties README.md"
  system "git commit -m 'chore(release): #{tag}'"
  system "git tag #{tag}"
  system "git push origin master --tags" or abort "Push failed"

  # GitHub release
  if changelog && !changelog.empty?
    puts "Using changelog from plugin.xml as release notes"
    # Write changelog to temp file for gh release create
    release_notes = "/tmp/release_notes_#{version}.md"
    File.write(release_notes, changelog)
    system "gh release create #{tag} #{zip} --title #{tag} --notes-file #{release_notes}"
    File.delete(release_notes)
  else
    puts "No changelog in plugin.xml, using auto-generated notes"
    system "gh release create #{tag} #{zip} --title #{tag} --generate-notes"
  end

  abort "GitHub release failed" unless $?.success?
  puts "Release #{tag} published: https://github.com/magynhard/intellij-crystal/releases/tag/#{tag}"
end