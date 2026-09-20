# Assignment in condition (while/if/until/elsif)

while line = gets
  puts line
end

while value = channel.receive?
  process(value)
end

if match = regex.match(input)
  puts match[0]
end

until result = try_connect
  sleep 1
end

if x = compute
  puts x
elsif y = fallback
  puts y
end

# Compound assignment in conditions (same positions, all assign_ops)

iter = 0
while iter += 1
  break if iter > 2
end

n = 5
until n -= 1
  break if n <= 0
end

if x = compute
  puts x
elsif y ||= fallback
  puts y
end

w = 0
w &+= 1 if w == 0
