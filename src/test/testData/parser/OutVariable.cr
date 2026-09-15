# `out` is an ordinary local-variable name (the compiler only rejects it
# as a parameter name; slice/sort.cr). The `foo(out x)` forwarding form
# keeps its own argument rule.
out = v.to_unsafe
out.value = v[right]
out += 1
puts out

def use_out(v, other)
  wrap(out, other)
  take(out other)
end
