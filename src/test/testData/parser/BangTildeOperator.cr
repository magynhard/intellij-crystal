class Matcher
  def !~(other)
    !(self =~ other)
  end
end

abstract class AbstractMatcher
  abstract def !~(other)
end

matches = Matcher.new !~ /crystal/
explicit_match = Matcher.new.!~(/crystal/)
static_match = Matcher.!~(/crystal/)
check Matcher.new !~ /crystal/

macro expanded_match
  {{ left !~ right }}
end

{% if left !~ right %}
  def macro_controlled_match
    true
  end
{% end %}

def after_bang_tilde
  true
end
