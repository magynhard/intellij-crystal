def self.new(**options : **T)
  options
end

def merge(**other : **U) forall U
  other
end

def splat_without_restriction(**plain)
  plain
end

class AfterSplat
  def preserved
  end
end
