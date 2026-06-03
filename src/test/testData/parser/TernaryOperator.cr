# Ternary Operator Examples
# condition ? then_value : else_value

# Basic ternary
x = 10
result = x > 5 ? "big" : "small"
puts result # => "big"

# Ternary in assignment
age = 20
status = age >= 18 ? "adult" : "minor"

# Nested ternary (use sparingly)
score = 85
grade = score >= 90 ? "A" : score >= 80 ? "B" : score >= 70 ? "C" : "F"

# Ternary with method calls
name = ""
display = name.empty? ? "Anonymous" : name.capitalize

# Ternary in string interpolation
count = 3
puts "#{count} item#{count == 1 ? "" : "s"}"

# Ternary as method argument
def log(message : String, level : String = "info")
  puts "[#{level.upcase}] #{message}"
end

debug = true
log("Starting...", debug ? "debug" : "info")

# Ternary with nil check
value : String? = nil
safe = value ? value.upcase : "default"

# Ternary returning different types (union)
flag = true
mixed = flag ? 42 : "hello" # Type is Int32 | String

# Ternary in array literal
include_admin = true
users = [
  "alice",
  "bob",
  include_admin ? "admin" : nil,
].compact

# Ternary for conditional initialization
class Config
  getter mode : String

  def initialize(production : Bool)
    @mode = production ? "prod" : "dev"
    @port = production ? 443 : 3000
    @debug = production ? false : true
  end
end

# Ternary vs if-expression (both valid in Crystal)
# These are equivalent:
a = x > 0 ? x : -x          # ternary
b = if x > 0 then x else -x end  # if-expression (rarely used inline)

# Ternary with boolean expressions
is_valid = true
has_permission = false
can_proceed = is_valid && has_permission ? "yes" : "no"

# Ternary in hash values
env = "production"
config = {
  "host"    => env == "production" ? "0.0.0.0" : "localhost",
  "port"    => env == "production" ? 80 : 3000,
  "workers" => env == "production" ? 8 : 1,
}

# Ternary with comparison operators
def clamp(value : Int32, min : Int32, max : Int32) : Int32
  value < min ? min : value > max ? max : value
end

# Ternary for safe navigation alternative
arr = [1, 2, 3]
first = arr.size > 0 ? arr[0] : nil

# Ternary with blocks/procs
handler = ENV.has_key?("VERBOSE") ? ->(msg : String) { puts "[VERBOSE] #{msg}" } : ->(msg : String) { }
