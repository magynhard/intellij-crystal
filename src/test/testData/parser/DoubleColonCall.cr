# ::-qualified calls: `::method(...)` and `::method arg` (bare command prefix),
# and `::Type(...)` constructor calls.
::raise "boom"

::raise CSV::MalformedCSVError.new("bad")

::puts "hello"

x = ::compute(1, 2)

::Foo.bar(3)
