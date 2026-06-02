# Multi-Assignment Examples
# a, b = 1, 2 — parallel/destructuring assignment

# Basic multi-assignment
a, b = 1, 2
x, y, z = "hello", 42, true

# Swap values
a, b = b, a

# From method returning tuple
def min_max(arr : Array(Int32))
  {arr.min, arr.max}
end

min, max = min_max([3, 1, 4, 1, 5])

# Destructuring with more values than variables (extras ignored)
first, second = [1, 2, 3, 4, 5]

# Fewer values than variables (nil assigned)
a, b, c = 1, 2

# Splat in multi-assignment
first, *rest = [1, 2, 3, 4, 5]
# first = 1, rest = [2, 3, 4, 5]

*head, last = [1, 2, 3, 4, 5]
# head = [1, 2, 3, 4], last = 5

first, *middle, last = [1, 2, 3, 4, 5]
# first = 1, middle = [2, 3, 4], last = 5

# Nested tuple destructuring
a, (b, c) = 1, {2, 3}
# a = 1, b = 2, c = 3

# Multi-assign from named tuple
point = {x: 10, y: 20}

# In loops
pairs = [{1, "one"}, {2, "two"}, {3, "three"}]
pairs.each do |num, name|
  puts "#{num}: #{name}"
end

# Hash each with destructuring
hash = {"a" => 1, "b" => 2, "c" => 3}
hash.each do |key, value|
  puts "#{key} => #{value}"
end

# Multi-return from blocks
result = [1, 2, 3].reduce({0, 0}) do |acc, element|
  {acc[0] + element, acc[1] + 1}
end
sum, count = result

# Ignoring values with underscore
_, second, _ = {1, 2, 3}
_, value = some_method_returning_tuple

# Multi-assignment in class
class Point
  getter x : Int32
  getter y : Int32

  def initialize(coords : Tuple(Int32, Int32))
    @x, @y = coords
  end

  def to_tuple
    {@x, @y}
  end
end

# Multi-assignment with method calls
width, height = get_dimensions()
name, age, email = parse_user_data(input)

# Multi-assignment with type restriction
a : Int32, b : String = 42, "hello"

# Chained assignment
a = b = c = 0

# Destructuring block params
result = [1, 2, 3].reduce({0, 0}) do |(sum, count), element|
  {sum + element, count + 1}
end
