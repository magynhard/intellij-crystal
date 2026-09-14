def map(&block : Int32 -> Int32)
  super { |i| yield i }
end

def each
  super do |i|
    yield i
  end
end

def first
  previous_def { |x| x }
end

class AfterSuper
  def preserved
  end
end
