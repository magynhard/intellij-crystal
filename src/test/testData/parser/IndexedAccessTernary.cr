def fallback(values, index)
  values[index] ? values[index] : 0
end

def consume(value)
end

def bare_fallback(values, index)
  consume values[index] ? values[index] : 0
end

first = [1][0] ? 1 : 0
safe = [1][2]?
mapped = [[1]].map(&.[0]?)

class AfterTernary
  def preserved
  end
end
