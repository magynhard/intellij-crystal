# A tight binary operator after an operand is binary, never the start of a bare
# splat/unary argument list: Crystal only starts bare arguments when the token
# after the callee is preceded by whitespace (parse_call_args `when .space?`),
# and `*`/`**` additionally reject whitespace after the operator. Regression:
# reply's `move_abs_cursor(x: indent*2, y: @y + 1)` bound `*2, y: ...` as bare
# arguments of `indent` and stranded the enclosing call's `y`.

def move_abs_cursor(@x, @y)
end

indent = 2
move_abs_cursor(x: indent*2, y: @y + 1)

total = base*c
diff = a-b
power = base**exponent

# Spaced forms stay bare arguments (`value *2`) or binary (`base * factor`).
scaled = value *2
spaced = base * factor
subtracted = a - b

# Trailing declaration proves the enclosing statement boundaries.
def after_tight_operators
end
