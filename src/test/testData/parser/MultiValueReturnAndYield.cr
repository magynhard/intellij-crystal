# Multi-value `return` / `break` / `next` and multi-argument `yield`.
# Wrapped in method bodies so the bare statements are in a valid context.

def build
  return new(a), new(b)
end

def consume
  return
end

def emit
  yield a, b, c
end
