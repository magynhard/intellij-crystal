line_text = "lol"
fix = line_text.sub(/\.map(\s*\{.*\})\s*\.sum/) { ".sum#{$1}" }

# Special global variables in string interpolation
a = "#{$1}"
b = "#{$~}"
c = "#{$?}"
d = "#{$stdout}"

# Globals combined with other expressions
e = "match=#{$1.upcase} len=#{$1.size}"

# Regex interpolation
f = /x#{$1}y/

# Command interpolation
g = `echo #{$1}`

# Heredoc interpolation
h = <<-TXT
  value: #{$1}
TXT

# Percent literal interpolation
i = %Q(#{ $1 })

# Nested interpolation with globals
j = "outer #{ "inner #{$1}" } end"

# Top-level global variables (regression guard)
$counter = 0
puts $1
puts $~
