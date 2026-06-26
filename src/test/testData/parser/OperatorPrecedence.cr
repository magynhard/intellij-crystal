# Operator Precedence Examples
# Demonstrating various operators and their precedence levels

# Crystal operator precedence (highest to lowest):
# 1. ! ~ + (unary)
# 2. **
# 3. * / // %
# 4. + - (binary)
# 5. << >>
# 6. & (binary AND)
# 7. | ^ (binary OR, XOR)
# 8. == != < <= > >= <=> === =~
# 9. && (logical AND)
# 10. || (logical OR)
# 11. .. ... (range)
# 12. ? : (ternary)
# 13. = += -= *= /= //= %= **= <<= >>= &= |= ^= &&= ||=
# 14. not (low-precedence logical not — Crystal doesn't have this, uses !)

# Arithmetic precedence
a = 2 + 3 * 4       # => 14 (not 20)
b = (2 + 3) * 4     # => 20
c = 2 ** 3 ** 2     # => 512 (right-associative: 2 ** (3 ** 2) = 2 ** 9)
d = 10 - 4 - 2      # => 4 (left-associative: (10 - 4) - 2)
e = 10 / 3          # => 3 (integer division)
f = 10 // 3         # => 3 (floor division)
g = 10 % 3          # => 1 (modulo)

# Bitwise operators
x = 0b1100 & 0b1010   # => 0b1000 (8)
y = 0b1100 | 0b1010   # => 0b1110 (14)
z = 0b1100 ^ 0b1010   # => 0b0110 (6)
w = ~0b1100            # => bitwise NOT
s = 1 << 4            # => 16
t = 32 >> 2           # => 8

# Mixed bitwise and arithmetic
result = 3 + 5 << 2   # => (3 + 5) << 2 = 32 (arithmetic binds tighter)
result2 = 1 | 2 & 3   # => 1 | (2 & 3) = 3 (& binds tighter than |)

# Comparison operators
a = 1 < 2             # => true
b = 3 <=> 5           # => -1
c = "hello" === /hel/ # => true (pattern match)
d = "abc" =~ /b/      # => 1 (match index)

# Logical operators
a = true && false      # => false
b = true || false      # => true
c = !true              # => false

# Short-circuit evaluation
result = nil || "default"           # => "default"
result = "value" || "default"       # => "value"
result = "first" && "second"        # => "second"

# Ternary operator
max = a > b ? a : b
sign = x > 0 ? "positive" : x < 0 ? "negative" : "zero"

# Range operators
inclusive = 1..10    # includes 10
exclusive = 1...10   # excludes 10

# Range in context (precedence matters)
range = 1 + 2..4 + 5  # => 3..9

# Range with omitted start/end (inside bracket access)
arr = [1, 2, 3, 4, 5]
from_start = arr[..2]
from_start_excl = arr[...2]
to_end = arr[1..]
to_end_excl = arr[1...]
full_range = arr[..]
full_range_excl = arr[...]

# Compound assignment
x = 10
x += 5    # x = x + 5
x -= 3    # x = x - 3
x *= 2    # x = x * 2
x //= 4   # x = x // 4
x **= 2   # x = x ** 2
x &= 0xFF # x = x & 0xFF
x |= 0x10 # x = x | 0x10
x ^= 0x01 # x = x ^ 0x01
x <<= 2   # x = x << 2
x >>= 1   # x = x >> 1
x &&= 5   # x = x && 5 (assigns if truthy)
x ||= 10  # x = x || 10 (assigns if falsy/nil)

# Operator overloading
struct Vector2
  getter x : Float64
  getter y : Float64

  def initialize(@x, @y)
  end

  def +(other : Vector2) : Vector2
    Vector2.new(@x + other.x, @y + other.y)
  end

  def -(other : Vector2) : Vector2
    Vector2.new(@x - other.x, @y - other.y)
  end

  def *(scalar : Float64) : Vector2
    Vector2.new(@x * scalar, @y * scalar)
  end

  def /(scalar : Float64) : Vector2
    Vector2.new(@x / scalar, @y / scalar)
  end

  def <=>(other : Vector2) : Int32
    magnitude <=> other.magnitude
  end

  def magnitude : Float64
    Math.sqrt(@x ** 2 + @y ** 2)
  end

  def [](index : Int32) : Float64
    case index
    when 0 then @x
    when 1 then @y
    else raise IndexError.new
    end
  end

  def []=(index : Int32, value : Float64)
    case index
    when 0 then @x = value
    when 1 then @y = value
    else raise IndexError.new
    end
  end
end

v1 = Vector2.new(1.0, 2.0)
v2 = Vector2.new(3.0, 4.0)
v3 = v1 + v2 * 2.0   # => v1 + (v2 * 2.0) due to precedence

# Unary operators
struct Vector2
  def - : Vector2
    Vector2.new(-@x, -@y)
  end

  def ~ : Vector2
    Vector2.new(@y, @x) # swap (perpendicular)
  end
end

negated = -v1
perpendicular = ~v1
