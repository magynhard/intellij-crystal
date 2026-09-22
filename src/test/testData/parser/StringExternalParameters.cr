def fetch("http-header" internal)
  internal
end

def stored("label" @value : String)
  value
end

def escaped("a\"b" plain)
  plain
end

def typed("x-y" body : Int32 = 1)
  body
end
