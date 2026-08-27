# Closeless parenthesized calls: in Crystal the ')' after a same-line heredoc
# delimiter belongs to the call; this lexer folds it into the literal as a second
# HEREDOC_START, so these argument lists end without an RPAREN token and bind
# through the guarded closeless branch of call_args.
f(<<-X)
  only
X

f(alpha, <<-Y)
  first then heredoc last
Y

f(one, two, <<-THREE)
  third is a heredoc
THREE

g("closed", "list") # strict path regression anchor

h(closed_first, <<-Z)
  chained postfix after closeless list continues normally
Z

w(<<-A)
  heredoc alone: single-START variant via content-terminator A-line? No — the
A

# mid-list standalone-line heredoc inside an otherwise closed call still parses
# through the STRICT path (RPAREN exists later):
v(<<-M, 42)
body one
M
puts :done

# MULTI: two same-line delimiters in one call (user report shape)
assert_diff(rule_a, <<-FIRST, <<-SECOND)
  def hello
    puts "hello"
  end
FIRST
  def world
    puts "world"
  end
SECOND

# MIXED: normal args interleaved AND trailing normal arg after second delimiter
process_data(<<-INPUT, 42, <<-EXPECTED, true)
  user_id: 10
INPUT
  status: ok
EXPECTED
