# pointerof and offsetof Examples
# Low-level memory access

# pointerof — get pointer to a variable
x = 42
ptr = pointerof(x)
puts ptr.value    # => 42
ptr.value = 100
puts x            # => 100

# pointerof with instance variables
class Data
  @value : Int32 = 0

  def value_ptr : Pointer(Int32)
    pointerof(@value)
  end

  def set_via_pointer(new_value : Int32)
    pointerof(@value).value = new_value
  end
end

data = Data.new
data.set_via_pointer(42)

# pointerof with array elements (via Slice)
arr = Slice(Int32).new(5) { |i| i * 10 }
ptr = arr.to_unsafe
puts ptr[0] # => 0
puts ptr[2] # => 20

# Pointer arithmetic
ptr2 = ptr + 3
puts ptr2.value # => 30

# pointerof in struct (stack-allocated)
struct Point
  property x : Float64
  property y : Float64

  def initialize(@x, @y)
  end

  def x_ptr : Pointer(Float64)
    pointerof(@x)
  end

  def y_ptr : Pointer(Float64)
    pointerof(@y)
  end
end

point = Point.new(1.0, 2.0)
point.x_ptr.value = 5.0
puts point.x # => 5.0

# offsetof — get byte offset of a field within a struct/class
puts offsetof(Point, @x) # => 0
puts offsetof(Point, @y) # => 8

# offsetof for alignment verification
struct Packet
  @header : UInt8 = 0
  @length : UInt32 = 0
  @payload : UInt64 = 0
end

puts offsetof(Packet, @header)  # => 0
puts offsetof(Packet, @length)  # => 4 (aligned to 4 bytes)
puts offsetof(Packet, @payload) # => 8 (aligned to 8 bytes)

# Using offsetof for manual memory layout
struct Vertex
  @position : StaticArray(Float32, 3) = StaticArray(Float32, 3).new(0.0_f32)
  @normal : StaticArray(Float32, 3) = StaticArray(Float32, 3).new(0.0_f32)
  @uv : StaticArray(Float32, 2) = StaticArray(Float32, 2).new(0.0_f32)
end

position_offset = offsetof(Vertex, @position) # => 0
normal_offset = offsetof(Vertex, @normal)     # => 12
uv_offset = offsetof(Vertex, @uv)             # => 24

# Practical: C interop with pointerof
lib LibC
  fun memcpy(dest : Void*, src : Void*, n : LibC::SizeT) : Void*
  fun memset(s : Void*, c : Int32, n : LibC::SizeT) : Void*
end

# Copy struct bytes
src_point = Point.new(3.0, 4.0)
dst_point = Point.new(0.0, 0.0)
LibC.memcpy(pointerof(dst_point).as(Void*), pointerof(src_point).as(Void*), sizeof(Point))

# Zero out memory
buffer = uninitialized StaticArray(UInt8, 256)
LibC.memset(pointerof(buffer).as(Void*), 0, 256)

# Pointer to local for C function calls
lib LibSomeLib
  fun get_value(out_ptr : Int32*) : Int32
end

result = uninitialized Int32
# LibSomeLib.get_value(pointerof(result))

# sizeof and instance_sizeof
puts sizeof(Int32)            # => 4
puts sizeof(Float64)          # => 8
puts sizeof(Point)            # => 16
puts sizeof(Pointer(Void))    # => 8 (on 64-bit)
puts instance_sizeof(Data)    # instance size including header

# Combining pointerof with Slice for zero-copy views
struct Matrix4
  @data : StaticArray(Float32, 16) = StaticArray(Float32, 16).new(0.0_f32)

  def to_slice : Slice(Float32)
    Slice.new(pointerof(@data).as(Pointer(Float32)), 16)
  end

  def [](row : Int32, col : Int32) : Float32
    @data[row * 4 + col]
  end

  def []=(row : Int32, col : Int32, value : Float32)
    @data[row * 4 + col] = value
  end
end
