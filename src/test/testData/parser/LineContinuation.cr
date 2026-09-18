# Backslash line continuation and range with newlines

# Backslash continues expression to next line
message = true \
  ? "yes" \
  : "no"

result = 1 + \
  2 + \
  3

# A plain newline after the operator ends the range: the compiler reads
# these as endless ranges followed by separate statements
endless_range = 0..
  10

exclusive_endless = 0...
  array_size

# Backslash after the operator keeps one logical line: still a full range
backslash_range = 1..\
  5

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
