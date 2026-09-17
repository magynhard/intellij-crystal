put_i64 = {
  operands: [value : Int64],
  push:     true,
  code:     value,
}

multi = {
  pop_values: [a : Int32, b : Int32],
}

with_default = [value : Int64 = 0]
ivar_target = [@pending : Bool]

plain = [value]
empty_typed = [] of Int32
conditional = flag ? first : second

macro_valued = {
  code: {% if flag?(:win32) %} first_call(value) {% else %} second_call(value) {% end %},
}

rocket_macro_valued = {
  "code" => {% if flag?(:win32) %} first_call(value) {% else %} second_call(value) {% end %},
}

{% begin %}
  generated_table = {
    first_entry: {
      code: foo,
    },
    {% for n in [8, 16] %}
      generated{{n}}: {
        pop_values: [value : UInt{{n}}],
        code: LibIntrinsics.bitreverse{{n}}(value),
      },
    {% end %}
    last_entry: {
      code: bar,
    },
  }
{% end %}

def after_type_declaration_elements
end
