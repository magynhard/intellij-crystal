
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

desc "Build plugin and store it in build/distributions"
task :build do |t|
  system "./gradlew buildPlugin"
end