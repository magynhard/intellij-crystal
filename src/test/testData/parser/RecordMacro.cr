# Record macro examples
# Crystal's built-in record macro creates a struct with getters

record Point, x : Int32, y : Int32

record Config, host : String, port : Int32 = 80, ssl : Bool = false

record User, name : String = "anonymous", age : Int32 = 0, admin : Bool = false

record Vec3D, x : Float64 = 0.0, y : Float64 = 0.0, z : Float64 = 0.0

record Options, flag : Bool = true, count : Int32 = 5

# Keyword field names (`end`) bind through keyword_identifier, not IDENTIFIER
record Span, start : Int32, end : Int32

record Range, begin : Int32, end : Int32 = 0

# Shorthand instance/class variable fields bind to the bare name
record Shorthand, @x : Int32, @@y : Int32 = 0

