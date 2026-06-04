# &.method shorthand — short block syntax

arr = ["hello", "world", "crystal"]

# Basic &.method
upcased = arr.map(&.upcase)
sizes = arr.map(&.size)
reversed = arr.map(&.reverse)

# &.method with predicate
long_words = arr.select(&.size.>(5))
starts_h = arr.select(&.starts_with?("h"))
has_o = arr.any?(&.includes?("o"))

# Chained &.method
sorted = arr.sort_by(&.size)
first_chars = arr.map(&.chars.first)

# With numbers
numbers = [1, -2, 3, -4, 5]
positives = numbers.select(&.>(0))
absolute = numbers.map(&.abs)
even = numbers.select(&.even?)
odd = numbers.reject(&.even?)

# Compact map
maybe_values = [nil, 1, nil, 2, nil, 3]
values = maybe_values.compact

# &.method on complex types
users = [{name: "Alice", age: 30}, {name: "Bob", age: 25}]

# Negation with &.
non_nil = [nil, "a", nil, "b"].reject(&.nil?)

# &.method with to_s
items = [1, 2, 3]
strings = items.map(&.to_s)
formatted = items.map(&.to_s(16))

# Multiple &. in chain (each call gets its own block)
result = ["abc", "de", "f"]
  .reject(&.empty?)
  .sort_by(&.size)
  .map(&.upcase)
