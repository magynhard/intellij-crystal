# Regression anchor: assignments inside parentheses route through grouped
# expressions (bare path), NOT through call_args. The rescue-state analyzer
# (CrystalTypeSetResolver) depends on this exact shape.
def use
  value = 1
  begin
    consume(value = "ready")
  rescue
    value
  end
end
