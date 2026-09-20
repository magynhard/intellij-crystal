def run_op_tests(t, u, op)
end

run_op_tests(Int32, Int64, :+)
run_op_tests(Int32, Int64, :-)
run_op_tests(Int32, Int64, :*)
run_op_tests(Int32, Int64, :/)
run_op_tests(Int32, Int64, :==)

ops = [:+, :-, :==, :[], :[]?, :[]=, :<=>, :===, :=~, :!~, :&, :|, :^, :~, :**, :&**, :>>, :<<, :%, :<, :<=, :>, :>=, :!, :!=, :/, ://, :&+, :&-, :&*]

same = :+ == :+

def check(op)
end

check(:+)

kind = :+

case kind
when :+
  "plus"
end
