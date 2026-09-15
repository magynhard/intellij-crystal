# Bare pseudo-method callees on implicit self: the compiler routes `is_a?`
# and `responds_to?` through parse_var_or_call like ordinary calls
# (macros/methods.cr, humanize.cr).
def describe(node)
  if is_a?(NilLiteral) || is_a?(Nop)
    puts "nothing"
  end
end

if zero? || (responds_to?(:infinite?) && responds_to?(:nan?))
  puts "special"
end

kind = is_a? String
