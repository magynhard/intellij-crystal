x = case tag
when .int32?
  "#{value.v_int32}"
when .float?
  "#{value.v_float}_f32"
else
  "unknown"
end.tap do |result|
  puts result
end

y = if condition
  1
else
  2
end.tap do |v|
  v + 1
end
