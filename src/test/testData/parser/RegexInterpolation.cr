# Regex literal interpolation

# Simple regex without interpolation
/simple/

# Regex with flags
/simple/i

# Regex with interpolation
/hello #{name}/

# Regex with interpolation and flags
/hello #{name}/i

# Regex with complex expression
/#{pattern}.*#{name}/

# Regex with escape sequences
/\d+\.#{x}/

# Regex after assignment (division context)
x = /hello #{name}/

# Regex in method call
puts /test #{x}/

# Multiple regexes
a = /foo/
b = /bar #{x}/
