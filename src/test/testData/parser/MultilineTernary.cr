def bsearch(from, to)
  mid = (from < 0) == (to < 0) ? from + ((to - from) >> 1) : (from < -to) ? -(((-from - to - 1) >> 1) + 1) : ((from + to) >> 1)
end

value = enabled ? first :
  second

other = enabled ?
  first : second

class AfterTernary
  def preserved
  end
end
