# Macro-spliced numeric literals (primitives/slice_spec.cr)

{% for num, suffix in BUILTIN_NUMBER_SUFFIXES %}
  slice = Slice.literal(1_{{ suffix.id }}, 2_{{ suffix.id }}, 3_{{ suffix.id }})
  scaled = 1.5_{{ suffix.id }}
  sized = UInt8[1_{{ suffix.id }}]
{% end %}

def trailing : Int32
  1
end
