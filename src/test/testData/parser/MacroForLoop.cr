# Macro for loop with interpolation in method definition

{% for op in [:add, :subtract, :multiply] %}
  def self.{{op.id}}(a : Int32, b : Int32) : Int32
    {% if op == :add %}
      a + b
    {% elsif op == :subtract %}
      a - b
    {% else %}
      a * b
    {% end %}
  end
{% end %}
