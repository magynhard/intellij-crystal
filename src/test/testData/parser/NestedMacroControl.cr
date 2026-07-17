class Serialization
  {% for ivar in @type.instance_vars %}
    {% ann = ivar.annotation(YAML::Field) %}
    {% unless ann && ann[:ignore] %}
      {%
        x = 1
      %}
    {% end %}
  {% end %}

  yaml.mapping(reference: self) do
    z
  end
end
