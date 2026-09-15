def self.new(**options : **T)
  {% begin %}
    {
      {% for key in T %}
        {{ key.stringify }}: options[{{ key.symbolize }}],
      {% end %}
    }
  {% end %}
end

{
  before: 0,
  {% if flag?(:test) %}
    generated: 1,
  {% end %}
  after: 2,
}

{
  {% if flag?(:test) %}
  {% end %}
}

{
  "a" => 1,
  {% for k in keys %}
    {{ k }} => 2,
  {% end %}
}
