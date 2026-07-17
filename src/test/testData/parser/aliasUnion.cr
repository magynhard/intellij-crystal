struct Foo
  alias Type = Nil | Bool | Int64 | Float64 | String | Array(Foo) | Hash(String, Foo)
end
