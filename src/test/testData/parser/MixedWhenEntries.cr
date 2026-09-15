# Each when-entry chooses independently between the implicit comparison
# and the plain form (the compiler branches per entry;
# `when .<(0x20), 0x7f` in json/builder.cr).
case byte
when .<(0x20), 0x7f
  puts "control"
when .< 0, limit
  puts "mixed"
when found, .> max
  puts "plain first"
else
  puts "other"
end
