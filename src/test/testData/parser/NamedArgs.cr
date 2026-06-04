# Named arguments and double splat

# Named arguments in calls
def greet(name : String, greeting : String = "Hello")
  "#{greeting}, #{name}!"
end

puts greet(name: "World")
puts greet(name: "Crystal", greeting: "Hi")
puts greet("Direct", greeting: "Hey")

# Named args with positional mix
def connect(host : String, port : Int32, ssl : Bool = false)
  {host, port, ssl}
end

connect("localhost", port: 443, ssl: true)
connect("example.com", 80)
connect(host: "db.local", port: 5432)

# Double splat parameter
def configure(**options)
  options.each do |key, value|
    puts "#{key}: #{value}"
  end
end

configure(host: "localhost", port: 8080, debug: true)

# Double splat forwarding
def wrapper(**opts)
  configure(**opts)
end

# Double splat with regular params
def setup(name : String, **config)
  puts name
  config.each { |k, v| puts "  #{k}: #{v}" }
end

setup("MyApp", port: 3000, env: "production")

# Named tuple from double splat
def to_named_tuple(**args)
  args
end

result = to_named_tuple(x: 1, y: 2, z: 3)

# External name vs internal name
def move(to destination : String, from origin : String)
  "#{origin} -> #{destination}"
end

move(to: "Berlin", from: "Munich")

# Named args with block
def fetch(url : String, timeout : Int32 = 30, &block : String ->)
  block.call("response from #{url}")
end

fetch("https://example.com", timeout: 60) do |response|
  puts response
end

# Double splat with type restriction
def typed_options(**opts : Int32)
  opts
end

typed_options(width: 100, height: 200)

# Named args with newlines
very_complex_method_call(
  first_argument: compute_something(),
  second_argument: "a long string value",
  third_argument: 42,
)
