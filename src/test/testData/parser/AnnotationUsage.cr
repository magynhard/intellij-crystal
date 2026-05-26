@[Deprecated]
def old_method
end

@[JSON::Serializable]
class Person
end

@[Link("sqlite3")]
lib LibSQLite3
end

class Foo
  @[Deprecated("use bar instead")]
  def baz
  end
end

@[Flags]
enum Permissions
  Read
end
