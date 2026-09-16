module Mutable(T)
  {% begin %}
  {% if compare_versions(Crystal::VERSION, "1.1.1") >= 0 %}
  def map!(& : T -> _) : self
  {% else %}
  def map!(&)
  {% end %}
    each_index do |i|
      yield i
    end
    self
  end
  {% end %}

  def after_macro_method
  end
end
