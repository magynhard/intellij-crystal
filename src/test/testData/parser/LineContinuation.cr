# Backslash line continuation and range with newlines

# Backslash continues expression to next line
message = true \
  ? "yes" \
  : "no"

result = 1 + \
  2 + \
  3

# Range with newline after operator
full_range = 0..
  10

exclusive = 0...
  array_size

# Method chaining across newlines (DOT at line start)
filtered = items
  .select { |i| i > 0 }
  .map { |i| i * 2 }
  .reduce(0) { |sum, i| sum + i }

names = users
  .reject(&.nil?)
  .compact
  .sort
  .first(10)
