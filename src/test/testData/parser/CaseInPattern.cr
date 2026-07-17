case result = compute
in Tuple(Int32, Bool)
  result
in Errno
  raise "error"
end

case value
in String
  value
else
  nil
end
