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
