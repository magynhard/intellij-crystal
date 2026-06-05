# Macro with macro interpolation containing symbols

annotation Route
end

macro read_routes
  {% for method in @type.methods %}
    {% ann = method.annotation(Route) %}
    {% if ann %}
      puts "{{ann[:method]}} {{ann[:path]}} -> {{method.name}}"
    {% end %}
  {% end %}
end
