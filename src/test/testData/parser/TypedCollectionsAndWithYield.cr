class Builder
  def build
    Deque{1, 2, 3}
  end

  def configure(value)
    set = Set{
      "first",
      "second",
    }
    headers = HTTP::Headers{"Accept" => "text/plain"}
    single = Deque{value}
    empty = Deque{}
    explicit = Set(String){"a", "b"}
    explicit_generic = Deque(Int32){1, 2, 3}
    result = with self yield value
    first, second = with self yield value, value
    parenthesized = with self yield(value)
    result
  end

  def following
  end
end
