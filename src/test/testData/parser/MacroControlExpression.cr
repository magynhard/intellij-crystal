# Macro control ({% ... %}) is valid in expression position (constant/assignment RHS)
# and as a bare command/expression, matching stdlib usage such as
# `NULL = {% if flag?(:win32) %} 0 {% else %} nil {% end %}`.
NULL = {% if flag?(:win32) %}
  0
{% else %}
  nil
{% end %}

VALUE = {% if LibC.has_method?(:foo) %}
  1
{% else %}
  2
{% end %}

result = {% if true %} 10 {% else %} 20 {% end %} + 5
