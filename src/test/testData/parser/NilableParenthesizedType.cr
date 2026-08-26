class Hash(K, V)
  def initialize(block : (Hash(K, V), K -> V)? = nil, *, initial_capacity = nil)
  end

  def self.new(default_value : V, initial_capacity = nil)
  end
end

alias Callback = (String, Int32 -> Bool)?
alias EntryWithIndex = {Entry(K, V), Int32}?
alias NamedOptions = {name: String, count: Int32}?
