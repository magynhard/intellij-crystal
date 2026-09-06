record EntryMatch, pattern : String do
  def matches?(string) : Bool
    pattern == string
  end
end

record Value128, low : UInt64, high : UInt64 do
  def self.new(value : UInt128) : self
    new(low: value.to_u64!, high: value.unsafe_shr(64).to_u64!)
  end
end

record EmptyBody do
end

record(Parenthesized, value : Int32) do
  def doubled
    value * 2
  end
end

module Qualified
end

record Qualified::Entry, value : Int32 do
  def value_string
    value.to_s
  end
end

class AfterRecordBlocks
end
