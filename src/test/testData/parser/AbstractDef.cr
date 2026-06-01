module Comparable(T)
  abstract def <=>(other : T) : Int32
end

class SortedArray(T) < Array(T)
end
