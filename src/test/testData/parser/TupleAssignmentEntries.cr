# Tuple elements are assignment-level: the compiler parses every element
# with parse_op_assign_no_control (complex.cr, call_error.cr). `{a, b = 1}`
# stays Tuple[Var, Assign] — never a multi-assignment.
case {real_inf_sign = @real.infinite?, imag_inf_sign = @imag.infinite?}
in {Nil, Nil}
  puts "infinite"
end

case {arg_type = arg.type, arg}
when {TupleInstanceType, Splat}
  puts "tuple"
end

single = {value = compute, fallback}
plain = {a, b}
check = {a == 1, b}
