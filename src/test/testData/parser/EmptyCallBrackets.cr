# Empty tight brackets after an expression: zero-arity `[]` call, never an empty array literal

# Number `[]` macro with no elements (spec/std/number_spec.cr:398)
ary = Int64[]
floats = Float64[]
chars = Int32[]
ary << 1_i64

# With elements stays the index postfix
filled = Int64[1, 2, 3]
filled[1]
filled[1].should be_a(Int64)

# Empty brackets on a variable
foo = [1, 2]
no_args = foo[]

# Spaced brackets are NOT the empty-argument call (tight guard)
spaced = Int64 []

# Genuine empty literals keep their shapes
empty = [] of Int64
deep = [] of Array(Int64)
hashish = {} of String => Int32
