dir = "cat-#{rule_id.split('-').last.rjust(3, '0')}"

# Plain char literal inside interpolation
a = "#{'x'}"

# Escape char literals inside interpolation
b = "#{pad(text, '\t')}"
c = "newline#{'\n'}end"
d = "quote#{'\''}end"

# Char literals in other interpolating literals
e = /sep#{'\n'}/
f = `echo #{'z'}`
g = <<-TXT
  pad: #{'h'}
TXT
h = %Q(#{'q'})

# Nested strings around char literals
i = "outer #{ "inner #{'-'}" } end"

# Top-level char literals (regression guard)
j = '-'
k = '\u{1F600}'
l = [1, 2].join(',')

# Macro control comparing against a char literal
macro flags_macro
  {% if x == 'a' %}
    flag_a
  {% end %}
end
