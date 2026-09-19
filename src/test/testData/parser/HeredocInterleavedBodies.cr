def assert_error(data, message)
end

def assert_warning(data, message)
end

def combine(first, second)
end

exc = assert_error <<-CRYSTAL,
  abstract class Foo
  end
  CRYSTAL
  "must be implemented"

assert_warning <<-CRYSTAL, "deprecated"
  @[Deprecated]
  class Foo
  end
  CRYSTAL

a, b = combine <<-VALUES, "extra"
  one
  VALUES

yield <<-YIELD
  yielded
  YIELD

yield combine(<<-NESTED)
  nested
  NESTED

combine <<-PLAIN, "same line"
  plain
  PLAIN
