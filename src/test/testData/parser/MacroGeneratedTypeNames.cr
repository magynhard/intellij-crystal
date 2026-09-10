# Macro-generated type names and operator-headed compound method names
# (stdlib primitives.cr:435/480/508/560 and friends)

{% begin %}
  {% nums = %w(Int8 Float64) %}
  {% ops = {
     "and" => "&",
     "or" => "|",
   } %}
  {% for num in nums %}
    struct {{num.id}}
      {% for name, op in ops %}
        @[::Primitive(:binary)]
        def &{{ops[name].id}}(other : {{num.id}}) : self
        end
      {% end %}

      def static_helper : self
        self
      end
    end
  {% end %}

  class {{("K" + "lass").id}} < View
    def initialize(@value : Float64)
    end
  end

  module {{("M" + "od").id}}
  end

  alias {{("A" + "l").id}} = Int32
{% end %}

# Plain definitions are unaffected
struct Int8
  def &(other : Int8) : self
    self
  end
end
