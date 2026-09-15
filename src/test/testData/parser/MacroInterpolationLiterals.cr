# Numeric literals inside macro interpolations and controls lex exactly like
# plain code: hex/octal/binary literals, integer suffixes, and floats.
small_step = {{ Limb == UInt64 ? 27_u32 : 13_u32 }}
magic = {{ flag?(:bits64) ? 0x20b : 0x10b }}
permissions = {{ 0o755 }}
flags = {{ 0b101 }}
ratio = {{ 1.5 }}
big = {{ 1e3 }}

{% if 0x20 == 0x20 %}
  hex_control_ok = true
{% end %}

# Integer division inside string interpolation.
version = "#{number // 10_000}.#{number % 10_000 // 100}"

# A chained call on a block value inside a macro body: `end.should` must
# close the `do` block (depth--) instead of swallowing the macro's END.
macro assert_built(call)
  ::String.build do |io|
    io << "x"
  end.should(be_truthy)
end
