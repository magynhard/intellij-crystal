class Clock
  def self.current : Int32
    clock = {% if flag?(:darwin) %} 1 {% else %} 2 {% end %}
    clock
  end

  EOL = {% if flag?(:windows) %} "\r\n" {% else %} "\n" {% end %}

  BIGINT_LIMBS = {{ BIGINT_BITS // LIMB_BITS }}

  property n_threads : Int32 = {% if flag?(:linux) %} 4 {% else %} 1 {% end %}

  def check : Bool
    {{ @type <= Int32 }}
  end
end

case value
when {{ i }}, {{ j }}
  1
end

case instruction
in .{{name.id}}?
  2
end

def trailing : Int32
  1
end
