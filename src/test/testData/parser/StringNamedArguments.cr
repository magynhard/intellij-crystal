with_env("FOO": "bar") do
end

with_env "LIB": "foo;;bar" do
end

x = NamedTuple("a-b": String)

tuple = NamedTuple(a: Int32, "xyz b-23": Int32).new("a-b": "foo")

hash = {"a-b": "foo"}

def trailing : Int32
  1
end
