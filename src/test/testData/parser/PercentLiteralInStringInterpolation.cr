pending = true
result = "prefix #{ %(pending "GB9c" { ) if pending } suffix"
modulo = "#{a % b}"
nested = "#{ %(a #{b} c) }"

# Raw %q inside interpolation: no escapes, no interpolation (expectations_spec.cr)
raw_tight = "#{%q(a\tb\nc).inspect}"
raw_spaced = "#{ %q(a\tb\nc).inspect }"
raw_empty = "#{%q()}"

def after_percent_interpolation
end
