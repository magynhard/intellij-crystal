lib LibC
  fun init(out @handle : Int32)
  fun read(out @buf : UInt8*, count : Int32)
end

def foo(out @x : Int32)
  @x
end

def bar(out y : Int32)
  y
end

LibC.init(out @h)
