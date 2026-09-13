# Macro fresh variables between macro control tags (iterator.cr ZipIterator).
private struct ZipIterator(Is)
  def next
    {% begin %}
      {% for i in 0...Is.size %}
        %value{i} = @iterators[{{ i }}].next
        return stop if %value{i}.is_a?(Stop)
      {% end %}
    {% end %}
  end
end

# Bare fresh variables without key (math_spec.cr isqrt loop).
def isqrt_cases
  {% begin %}
    %val = 42
    %exp = 6
    Math.isqrt(%val).should eq(%exp)
  {% end %}
end

# Namedtuple deserialization bindings (json/from_json.cr).
def NamedTuple.new(pull : JSON::PullParser)
  {% begin %}
    {% for key, type in T %}
      {% if type.nilable? %}
        %var{key.id} = nil
      {% else %}
        %var{key.id} = uninitialized typeof(element_type({{ key.symbolize }}))
      {% end %}
    {% end %}
  {% end %}
  instance = self.allocate
end

# Keyed fresh variables with expanded keys (json/from_json.cr style).
def populate(source : JSON::PullParser)
  {% begin %}
    {% for key, type in T %}
      %var{key.id} = nil
      {% if type.nilable? %}
        %found{key.id} = false
      {% else %}
        %found{key.id} = true
      {% end %}
    {% end %}
    %var{type} = source.read_string
  {% end %}
done_assign = 1
end

# Modulo keeps the operator reading when a left operand precedes `%val`.
mod_result = 10 % val
other_mod = count % item
