NamedTuple.new(
  name: {{ key.id.stringify }},
  value: %var{key.id}
)

NamedTuple.new(
  {% for key in T.keys %}
    {{ key.id.stringify }}: %var{key.id},
  {% end %}
)
