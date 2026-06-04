# Proc/Lambda literals and method references

# Basic proc literal with type annotations
add = ->(x : Int32, y : Int32) { x + y }
puts add.call(1, 2)

# No-arg proc
greet = ->{ puts "hello" }
greet.call

# Single arg proc
double = ->(x : Int32) { x * 2 }

# Multiline proc body
compute = ->(x : Int32, y : Int32) {
  result = x * y
  result + 1
}

# Proc type annotations
callback : Proc(Int32, Int32, Int32) = add
handler : Proc(String, Nil) = ->(msg : String) { puts msg }

# Proc as parameter
def apply(value : Int32, &block : Int32 -> Int32)
  block.call(value)
end

def run_callback(cb : Proc(Int32, Int32))
  cb.call(42)
end

# Method reference (->method_name)
puts_ref = ->puts(String)

# Method reference on instance
str = "hello"
upcase_ref = ->str.upcase

# Method reference with argument types
arr = [3, 1, 2]
sort_ref = ->arr.sort

# Proc#call
fn = ->(x : Int32) { x * 10 }
result = fn.call(5)

# Proc stored in variable with type
my_proc : Proc(Int32, String) = ->(n : Int32) { n.to_s }

# Proc in data structures
handlers = [] of Proc(String, Nil)
handlers << ->(msg : String) { puts msg }
handlers << ->(msg : String) { STDERR.puts msg }

# Passing proc to method
def with_retry(times : Int32, &block : -> Bool)
  times.times do
    return if block.call
  end
end

# Closure — proc captures variables
counter = 0
increment = ->{ counter += 1; counter }
increment.call
increment.call
puts counter  # 2

# Proc with newlines
long_proc = ->(
  first_arg : Int32,
  second_arg : String
) {
  "#{second_arg}: #{first_arg}"
}
