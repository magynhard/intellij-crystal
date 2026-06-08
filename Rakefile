
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

desc "Build plugin release by bumping version and store it in build/distributions"
task :release => :bump_version do |t|
  system "./gradlew buildPlugin"
end