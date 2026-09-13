# typeof accepts comma-separated expression lists in expression and type context

def type_union(first, second)
  typeof(first, second)
end

def typed_pointer(first, second)
  Pointer(typeof(first, second)).null
end

def multiline(first, second)
  typeof(
    first,
    second,
  )
end

def nested(first, second)
  typeof({first, second}, build(first, second))
end
