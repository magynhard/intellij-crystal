# Macro Body Parsing Examples
# {% %}, {{ }}, {% for %}, {% if %}

# Simple macro
macro define_method(name, content)
  def {{name.id}}
    {{content}}
  end
end

define_method(:greet, "Hello!")

# Macro with for loop
macro define_getters(*names)
  {% for name in names %}
    def {{name.id}}
      @{{name.id}}
    end
  {% end %}
end

# Macro with if/else
macro define_property(name, type, default = nil)
  {% if default %}
    @{{name.id}} : {{type}} = {{default}}
  {% else %}
    @{{name.id}} : {{type}}
  {% end %}

  def {{name.id}} : {{type}}
    @{{name.id}}
  end

  def {{name.id}}=(value : {{type}})
    @{{name.id}} = value
  end
end

# Macro with string interpolation and stringify
macro debug(expr)
  puts "#{{{expr.stringify}}} = #{{{expr}}}"
end

# Macro hooks
class MyClass
  macro inherited
    puts "{{@type.name}} inherited from {{@type.superclass}}"
  end

  macro method_added(method)
    {% if method.name == "initialize" %}
      puts "Constructor defined in {{@type.name}}"
    {% end %}
  end

  macro finished
    {% for method in @type.methods %}
      {% puts "Method: #{method.name}" %}
    {% end %}
  end
end

# Macro with env and conditional compilation
macro compile_time_check
  {% if env("DEBUG") %}
    puts "Debug mode enabled"
  {% end %}

  {% if flag?(:linux) %}
    PLATFORM = "linux"
  {% elsif flag?(:darwin) %}
    PLATFORM = "macos"
  {% elsif flag?(:win32) %}
    PLATFORM = "windows"
  {% end %}
end

# Fresh variables in macros
macro define_counter(name)
  %counter = 0

  def increment_{{name.id}}
    %counter += 1
  end

  def {{name.id}}_count
    %counter
  end
end

# Macro receiving block
macro with_logging(&block)
  puts "Before"
  {{block.body}}
  puts "After"
end

# Macro iterating over type info
macro define_json_mapping
  {% for ivar in @type.instance_vars %}
    def self.from_json_key_{{ivar.name.id}}(value)
      instance = allocate
      instance.@{{ivar.name.id}} = value.as({{ivar.type}})
      instance
    end
  {% end %}
end

# Enum auto-generation via macro
macro define_enum(name, *members)
  enum {{name.id}}
    {% for member in members %}
      {{member.id}}
    {% end %}
  end
end

define_enum(Color, Red, Green, Blue)

# Variadic macro
macro log(*args)
  print "[LOG] "
  {% for arg, index in args %}
    {% if index > 0 %}
      print ", "
    {% end %}
    print {{arg}}
  {% end %}
  puts
end
