# Implicit object call with bracket access

# Parenthesized: &.method and &.[index]
foo(&.to_s)
foo(&.first)
foo(&.[1])
foo(&.[](1))
foo(&.[1, 2])
foo(&.[0..1])
foo(&.[]?)
foo(&.[0] = 99)
