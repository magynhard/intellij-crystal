# Percent literal string interpolation

# Bare % — interpolating
x = "world"
%(hello #{x} end)

# %Q — interpolating
%Q(hello #{x} end)

# %q — NOT interpolating (literal)
%q(no #{interpolation} here)

# %r — interpolating regex
%r(pattern #{x})

# %x — interpolating command
%x(echo #{x})

# %w — NOT interpolating word array
%w(word #{x})

# %i — NOT interpolating symbol array
%i(sym #{x})

# Braces inside percent literal with interpolation
%({hello #{x}})

# Multiline percent literal with interpolation
%(
  line1 #{x}
  line2
)

# Interpolation with complex expression
%(result: #{1 + 2})

# Nested quotes inside interpolation
%("value is #{x}")
