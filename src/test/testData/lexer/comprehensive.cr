# This is a comment
class Greeter
  @name : String
  @@count = 0

  def initialize(@name : String)
    @@count += 1
  end

  def greet(greeting = "Hello")
    puts "#{greeting}, #{@name}! Count: #{@@count}"
  end

  def self.count
    @@count
  end
end

module Printable
  abstract def to_s : String
end

struct Point
  property x : Int32
  property y : Int32

  def initialize(@x, @y)
  end

  def distance(other : Point) : Float64
    Math.sqrt((@x - other.x) ** 2 + (@y - other.y) ** 2)
  end
end

enum Color
  Red
  Green
  Blue
end

# Literals
nil_val = nil
bool_val = true
int_val = 42_i64
hex_val = 0xFF
bin_val = 0b1010
oct_val = 0o777
float_val = 3.14_f32
char_val = 'a'
string_val = "hello world"
symbol_val = :my_symbol
regex_val = /[a-z]+/i
command_val = `echo hello`

# Control flow
if int_val > 0
  puts "positive"
elsif int_val == 0
  puts "zero"
else
  puts "negative"
end

unless bool_val
  puts "falsy"
end

case int_val
when 0..10
  puts "small"
when 11..100
  puts "medium"
else
  puts "large"
end

while int_val > 0
  int_val -= 1
end

until int_val >= 10
  int_val += 1
end

# Blocks and procs
arr = [1, 2, 3]
arr.each do |item|
  puts item
end

square = ->(x : Int32) { x * x }

# Type stuff
alias MyInt = Int32

x = 5
if x.is_a?(Int32)
  puts typeof(x)
end

result = x.as?(String)

# Exception handling
begin
  raise "oops"
rescue ex : Exception
  puts ex.message
ensure
  puts "done"
end

# Generics and unions
def identity(value : T) : T forall T
  value
end

my_var : Int32 | String = "hello"

# Macros
macro define_method(name, content)
  def {{name.id}}
    {{content}}
  end
end

# Note: $global_variables are not supported in Crystal
# Use @@class_variables instead

# Percent literals
arr_str = %w(foo bar baz)
arr_sym = %i(one two three)
paren_str = %(hello world)
bracket_str = %[brackets]
brace_str = %{braces}
angle_str = %<angles>
pipe_str = %|pipes|
nested = %(hello (world))
regex_lit = %r(pattern)

# Heredoc
text = <<-HEREDOC
  Hello
  World
  HEREDOC

name2 = "Crystal"
text2 = <<-END
  Hello #{name2}
  END

text3 = <<-'RAW'
  No interpolation here
  RAW
