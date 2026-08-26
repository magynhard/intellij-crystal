def test_method
  return [] of Result unless target = PAIRS[node.name]?
  target
end

# Assignment in postfix if
puts "found" if v = cache[key]?

# Assignment in postfix while / until
i = 0
puts i while n = (i += 1) < 3 ? i : nil
sleep 1 until done = finished?

# Assignment in postfix rescue
value = strict_parse rescue fallback = DEFAULTS[:fallback]

# Assignments in block-level conditions (regression guard)
if user = users.first?
  puts user.name
end
unless limit = config[:limit]?
  limit = 10
end

# Assignment in in-clause guard (forward-compatible; Crystal >= 1.21 rejects guards here)
case shape
in Circle if radius = shape.try(&.radius)
  puts radius
in Square
  puts "square"
end
