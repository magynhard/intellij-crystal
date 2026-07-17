# Grammar coverage for `record` definitions with a `do` body, nested `def`,
# and `record` without a body.

record EntryMatch, pattern : String do
  def matches?(string) : Bool
    true
  end
end

record RecursiveDirectories

x = foo do
  def bar
    1
  end
end
