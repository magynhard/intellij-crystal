# Rescue with larger union (backslash continuation — valid crystal)
begin
  some_thing
rescue ex : ArgumentError | \
            IndexError | \
            KeyError | \
            NilAssertionError | \
            TypeCastError
  puts "Value error: #{ex.class} — #{ex.message}"
end

def handle(payload : String | \
                    Int32) : Nil
  nil
end
