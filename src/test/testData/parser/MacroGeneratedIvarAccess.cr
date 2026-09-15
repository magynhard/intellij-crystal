struct Point
  def ==(other) : Bool
    if other.is_a?(self)
      {% for ivar in @type.instance_vars %}
        return false unless @{{ivar.id}} == other.@{{ivar.id}}
      {% end %}
      true
    else
      false
    end
  end
end
