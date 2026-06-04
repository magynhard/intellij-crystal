# Trailing commas — allowed in Crystal in most list contexts

# Method parameters
def foo(
  a : Int32,
  b : String,
  c : Float64,
)
  a
end

# Method call arguments
foo(
  1,
  "hello",
  3.14,
)

# Single-line trailing comma
foo(1, "hello", 3.14,)

# Array literal
arr = [
  1,
  2,
  3,
]

single_line = [1, 2, 3,]

# Hash literal
hash = {
  "name" => "Crystal",
  "version" => "1.0",
  "fast" => "yes",
}

# Tuple literal
tuple = {
  1,
  "hello",
  3.0,
}

# Named tuple literal
config = {
  host: "localhost",
  port: 8080,
  debug: true,
}

# Enum members (not trailing comma — Crystal doesn't allow it in enums)
# enum Color
#   Red
#   Green
#   Blue
# end

# Type arguments
alias Handler = Proc(
  Int32,
  String,
  Nil,
)

# Generic type parameters
class Container(
  T,
)
  @items = [] of T
end

# Block parameters
[1, 2, 3].each do |
  x,
|
  puts x
end
