# Real-stdlib parse patterns (int.cr / float.cr) that guard primitive
# completion: grammar gaps here silently remove Int32/Float64 from stub
# indexing and empty DOT completion on numeric literals.
struct Float64
  Number.expand_div [Int8, UInt8, Int16, UInt16, Int32, UInt32], Float64
  Number.expand_div [Float32], Float64
end

struct Int8
  MIN = -128_i8
  MAX = 127_i8

  def {{type.id}}.from_digits(digits : Enumerable(Int), base : Int = 10) : self
    num : {{type.id}} = 0
    multiplier : {{type.id}} = 1
    u, v = v, u if u > v
    num
  end

  def Float64.new(value)
    value.to_f64
  end
end
