struct Float
  def %(other : Float) : Float
    self * other
  end

  def %(other : Int32) : Float
    self * other.to_f
  end
end

x = 1.0 % 2.0
