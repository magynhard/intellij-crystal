# Command literals — backtick execution

# Basic command
output = `echo hello`
puts output

# Command with interpolation
name = "world"
greeting = `echo #{name}`

# Multi-word command
listing = `ls -la /tmp`

# Check exit status
`true`
success = $?.success?

`false`
failed = $?.success?

# Command in condition
if `which crystal`.chomp.size > 0
  puts "Crystal found"
end

# Command with pipes (shell interprets)
count = `ls /tmp | wc -l`

# Multiline command (using backslash inside backticks)
result = `echo "line 1" && \
  echo "line 2"`

# Percent command literal %x()
files = %x(ls /tmp)
version = %x(crystal --version)

# Command in assignment
class System
  def self.hostname
    `hostname`.chomp
  end

  def self.uptime
    `uptime`.strip
  end
end

# $? after command
`echo test`
exit_code = $?.exit_code
