# Implicit-object calls (`&.method`) combined with shorthand blocks and
# operators, plus the explicit `===` call form.

xs.each(&.unsafe_each { |x| yield x })

x = a.===(b)
