# Named Tuple Examples
# {name: "foo", age: 42}

# Basic named tuple literal
person = {name: "Alice", age: 30, active: true}
puts person[:name] # => "Alice"
puts person[:age]  # => 30

# Named tuple type annotation
point : {x: Int32, y: Int32} = {x: 10, y: 20}

# Named tuple as method parameter
def greet(user : {name: String, title: String})
  puts "Hello, #{user[:title]} #{user[:name]}!"
end

greet({name: "Smith", title: "Dr."})

# Named tuple as return type
def parse_address(raw : String) : {street: String, city: String, zip: String}
  parts = raw.split(", ")
  {street: parts[0], city: parts[1], zip: parts[2]}
end

address = parse_address("123 Main St, Springfield, 62701")
puts address[:city] # => "Springfield"

# Named tuple with different value types
config = {
  host:    "localhost",
  port:    8080,
  debug:   true,
  timeout: 30.0,
}

# Accessing named tuple fields
puts config[:host]
puts config[:port]

# Named tuple merge
defaults = {color: "red", size: 10, visible: true}
overrides = {color: "blue", size: 20}
# Note: Crystal doesn't have built-in merge for named tuples,
# but you can create a new one:
final = {color: overrides[:color], size: overrides[:size], visible: defaults[:visible]}

# Named tuple in generic context
alias Headers = {content_type: String, accept: String}

# Iterating named tuple
settings = {name: "app", version: "1.0", env: "production"}
settings.each do |key, value|
  puts "#{key}: #{value}"
end

# Named tuple size and keys
puts settings.size   # => 3
puts settings.keys   # => {:name, :version, :env}
puts settings.values # => {"app", "1.0", "production"}

# Named tuple from double splat
def make_config(**options)
  options # This is a NamedTuple
end

cfg = make_config(host: "db.local", port: 5432)

# Named tuple type alias
alias Point3D = {x: Float64, y: Float64, z: Float64}

origin : Point3D = {x: 0.0, y: 0.0, z: 0.0}

# Named tuple in array
users = [
  {name: "Alice", role: "admin"},
  {name: "Bob", role: "user"},
  {name: "Charlie", role: "moderator"},
]

users.each do |user|
  puts "#{user[:name]} (#{user[:role]})"
end

# Named tuple with from_json (via JSON::Serializable)
require "json"

# Comparing named tuples
a = {x: 1, y: 2}
b = {x: 1, y: 2}
puts a == b # => true

# Named tuple to_h
nt = {first: "John", last: "Doe"}
# Can convert keys to string hash:
hash = Hash(String, String).new
