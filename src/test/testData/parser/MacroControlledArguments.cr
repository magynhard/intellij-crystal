yield Tuple.new(
  {% for i in 0...U.size %}
    indexables[{{ i }}].unsafe_fetch(indices[{{ i }}]),
  {% end %}
)

consume(
  first,
  {% if flag %}
    second,
  {% end %}
)

generate(
  {% if flag %}
  {% end %}
)
