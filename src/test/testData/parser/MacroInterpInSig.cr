VERSION = {{ `pkg-config --modversion`.chomp.stringify }}

def {{type.id}}.from_digits(digits : Enumerable(Int), base : Int = 10) : self
  {{type.id}}.new
end

def self.decode(int : {{type.id}}.class, io : IO)
  int.to_i
end

private def int_as_float(i : Int{{ p }})
  i.to_f
end

BIGINT_LIMBS = {{ BIGINT_BITS // LIMB_BITS }}

dst[{{i}}] = 1

severity = {{severity}}
