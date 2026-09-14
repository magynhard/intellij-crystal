class Types
  def private?
    false
  end

  def private=(set_private)
    set_private
  end

  def self.private=(value)
    value
  end
end

class AfterSetter
  def preserved
  end
end
