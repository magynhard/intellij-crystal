# Wrapping operators with newlines
a = 1
b = 2
c = 3
d = 4

result = a &+
  b

result2 = (
  c &-
  d
)

h = a &** 31

x = a &+ b &* c

# Unary wrapping operators (compiler parse_prefix): `&-` and `&+` are prefix
# operators exactly like `-` and `+`; `&*`/`&**` stay binary.
negative = &-value
positive = &+value
spaced = &- value
nested = Pointer(T).new(self.address & (&-boundary))
ternary = value < 0 ? &-v : v
argument = call(&-(1_u64 << shift))
precedence = &-a &* b
binary_left_intact = a &- b
