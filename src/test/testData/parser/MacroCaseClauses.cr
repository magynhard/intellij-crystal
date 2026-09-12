case value
{% for i in values %}
when {{ i }}
  i
{% end %}
end

case opcode
{% for name in names %}
in .{{name.id}}?
  name
{% end %}
else
  nil
end

case mixed
when 1
  one
{% if flag?(:x) %}
when 2
  two
{% end %}
end

case compact; when 1; 1; end

def after_case
end
