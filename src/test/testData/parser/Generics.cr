# Full Generics Examples
# Array(T), forall T, constraints

# Generic class definition
class Container(T)
  @value : T

  def initialize(@value : T)
  end

  def value : T
    @value
  end

  def transform(& : T -> U) : Container(U) forall U
    Container(U).new(yield @value)
  end
end

# Generic struct
struct Pair(A, B)
  getter first : A
  getter second : B

  def initialize(@first : A, @second : B)
  end

  def swap : Pair(B, A)
    Pair(B, A).new(@second, @first)
  end
end

# Generic module with constraint
module Comparable(T)
  abstract def <=>(other : T) : Int32
end

# Generic method with forall
def identity(x : T) : T forall T
  x
end

def map_array(arr : Array(T), & : T -> U) : Array(U) forall T, U
  arr.map { |e| yield e }
end

# Multiple type parameters
class HashMap(K, V)
  def put(key : K, value : V)
  end

  def get(key : K) : V?
    nil
  end

  def each(& : K, V ->)
  end
end

# Type constraints / restrictions
def add(a : T, b : T) : T forall T
  a + b
end

# Nested generics
alias NestedMap = Hash(String, Array(Int32))
alias Callback = Proc(Array(String), Hash(String, Int32), Nil)

# Generic inheritance
class SortedArray(T) < Array(T)
end

# Usage examples
container = Container(String).new("hello")
pair = Pair(Int32, String).new(42, "world")
swapped = pair.swap

numbers = [1, 2, 3] of Int32
mapped = map_array(numbers) { |x| x.to_s }

