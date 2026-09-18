struct Vec3
  getter :x
  getter :y

  {% for op in %w(+ - * /) %}
    def {{op.id}}(other : Vec3)
      Vec3.new(@x {{op.id}} other.x, @y {{op.id}} other.y)
    end

    def {{op.id}}(other : Float)
      Vec3.new(@x {{op.id}} other)
    end
  {% end %}

  def after_generated_operators
  end
end
