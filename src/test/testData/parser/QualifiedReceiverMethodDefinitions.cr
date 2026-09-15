# Explicitly qualified method receivers: the constant path before the DOT
# owns the definition (`def Time::Location.new` in json/from_json.cr and
# yaml/from_yaml.cr), even at file top level or inside an unrelated type.
def Float64.new(value)
  value.to_f64
end

def Time::Location.new(pull : JSON::PullParser)
  load(pull.read_string)
end

def Time::Location.from_json_object_key?(key : String) : Time::Location
  load(key)
end

def Outer::Inner::Factory.build
  new
end

def Time::Location.zone=(zone : String)
  @zone = zone
end

struct Int8
  def Float64.new(value)
    value.to_f64
  end
end

def after_qualified_definition
  42
end
