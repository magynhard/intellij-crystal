# Regression anchor: a parenthesized assignment argument routes through
# call_args as ARGUMENT > ASSIGNMENT (matching the compiler's parse_op_assign
# in argument position), NOT through the bare grouped-expression fallback.
# The rescue-state analyzer covers this shape directly
# (CrystalTypeSetResolverTest.testRescueSeesPostArgumentAssignmentStateWhenEnclosingCallRaises).
def use
  value = 1
  begin
    consume(value = "ready")
  rescue
    value
  end
end
