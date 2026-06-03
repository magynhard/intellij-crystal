# Pattern Matching Examples (case...in)
# Crystal 1.x+ exhaustive pattern matching

# Basic pattern matching with types
def describe(value)
  case value
  in Int32
    "It's an integer: #{value}"
  in String
    "It's a string: #{value}"
  in Bool
    "It's a boolean: #{value}"
  in Nil
    "It's nil"
  end
end

# Pattern matching with tuples
def process_point(point : {Int32, Int32})
  case point
  in {0, 0}
    "origin"
  in {x, 0}
    "on x-axis at #{x}"
  in {0, y}
    "on y-axis at #{y}"
  in {x, y}
    "point at (#{x}, #{y})"
  end
end

# Pattern matching with named tuples
def handle_response(response : {status: Int32, body: String})
  case response
  in {status: 200, body: body}
    "Success: #{body}"
  in {status: 404, body: _}
    "Not found"
  in {status: 500, body: body}
    "Server error: #{body}"
  in {status: status, body: _}
    "HTTP #{status}"
  end
end

# Exhaustive enum matching
enum Direction
  North
  South
  East
  West
end

def move(direction : Direction)
  case direction
  in .north?
    {0, 1}
  in .south?
    {0, -1}
  in .east?
    {1, 0}
  in .west?
    {-1, 0}
  end
end

# Pattern matching with union types
alias JSON = Nil | Bool | Int64 | Float64 | String | Array(JSON) | Hash(String, JSON)

def stringify_json(value : JSON) : String
  case value
  in Nil
    "null"
  in Bool
    value.to_s
  in Int64
    value.to_s
  in Float64
    value.to_s
  in String
    "\"#{value}\""
  in Array(JSON)
    "[#{value.map { |v| stringify_json(v) }.join(", ")}]"
  in Hash(String, JSON)
    pairs = value.map { |k, v| "\"#{k}\": #{stringify_json(v)}" }
    "{#{pairs.join(", ")}}"
  end
end

# Pattern matching with variables (binding)
def categorize_age(age : Int32)
  case age
  in 0..2
    "baby"
  in 3..12
    "child"
  in 13..17
    "teenager"
  in 18..64
    "adult"
  in 65..
    "senior"
  end
end

# Nested pattern matching
def parse_command(input : {String, Array(String)})
  case input
  in {"quit", _}
    :quit
  in {"echo", args}
    puts args.join(" ")
  in {"add", [a, b]}
    puts (a.to_i + b.to_i).to_s
  in {cmd, _}
    puts "Unknown command: #{cmd}"
  end
end

# Pattern matching with guards (when + in hybrid)
def classify(value : Int32 | String)
  case value
  in Int32
    case value
    in .positive?
      "positive number"
    in .negative?
      "negative number"
    in .zero?
      "zero"
    end
  in String
    case value.size
    in 0
      "empty string"
    in 1..10
      "short string"
    in 11..
      "long string"
    end
  end
end
