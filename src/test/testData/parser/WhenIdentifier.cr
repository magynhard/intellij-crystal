# `when` is an ordinary identifier in nested positions (absent from the
# compiler's invalid_internal_name? list): block parameter, receiver, and
# argument (type_guess_visitor.cr). It stays unusable at statement level, so
# `when` clauses keep their structural parsing; a two-clause case below pins
# the clause dispatch.
node.whens.each do |when|
  guess_type(when.body)
end

puts when

case tag
when .int32?
  puts "int"
when .float?
  puts "float"
end
