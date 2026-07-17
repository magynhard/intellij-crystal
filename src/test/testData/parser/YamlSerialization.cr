class Foo
  {% begin %}
    {% options = @type.annotation(Foo::Options) %}
    {% emit_nulls = options && options[:emit_nulls] %}

    {% properties = {} of Nil => Nil %}
    {% for ivar in @type.instance_vars %}
      {% ann = ivar.annotation(Foo::Field) %}
      {% unless ann && (ann[:ignore] || ann[:ignore_serialize] == true) %}
        {%
          properties[ivar.id] = {
            key:              ((ann && ann[:key]) || ivar).id.stringify,
            converter:        ann && ann[:converter],
            emit_null:        (ann && (ann[:emit_null] != nil) ? ann[:emit_null] : emit_nulls),
            ignore_serialize: ann && ann[:ignore_serialize],
          }
        %}
      {% end %}
    {% end %}

    yaml.mapping(reference: self) do
      {% for name, value in properties %}
        _{{name}} = @{{name}}
      {% end %}
    end
  {% end %}
end