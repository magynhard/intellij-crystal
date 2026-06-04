# Empty collections with 'of' keyword

# Empty array with type
empty_arr = [] of Int32
strings = [] of String
nested = [] of Array(String)

# Empty hash with types
empty_hash = {} of String => Int32
config = {} of String => String | Int32 | Bool
nested_hash = {} of String => Array(Int32)

# Complex types
handlers = [] of Proc(String, Nil)
callbacks = {} of Symbol => Proc(Int32, Nil)

# Union types in of
mixed = [] of Int32 | String | Nil
multi = {} of String => Int32 | Float64

# Generic types
containers = [] of Array(Array(Int32))
maps = {} of String => Hash(String, Int32)

# With class/module paths
models = [] of MyApp::Models::User
errors = [] of Exception | Nil

# Using of in method return
def empty_list : Array(Int32)
  [] of Int32
end

def empty_map : Hash(String, Int32)
  {} of String => Int32
end

# Of with tuple types
tuples = [] of {Int32, String}
named_tuples = [] of {name: String, age: Int32}

# Assigning and using
arr = [] of Int32
arr << 1
arr << 2
arr << 3

hash = {} of String => Int32
hash["one"] = 1
hash["two"] = 2

# Of in instance variables
class Collection
  @items = [] of String
  @index = {} of String => Int32
  @callbacks = [] of Proc(String, Nil)

  def add(item : String)
    @index[item] = @items.size
    @items << item
  end
end

# Pointer/Slice types
buffers = [] of Slice(UInt8)
pointers = [] of Pointer(Int32)
