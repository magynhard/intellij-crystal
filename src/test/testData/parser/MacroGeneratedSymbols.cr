# Macro-generated symbols: colon + macro interpolation (colorize.cr fore/back,
# macros.cr generated getters, compiler instance_var_spec attribute indices)

def fore(color : Symbol) : self
  {% for name in COLORS %}
    if color == :{{name.id}}
      @fore = ColorANSI::{{name.camelcase.id}}
      return self
    end
  {% end %}
end

{% for property in properties %}
  getter :{{property.id}}
{% end %}

def probe(attr)
  attr[:{{var.name.id}}]?
end

def show
  :{{name.id}}.to_s
end
