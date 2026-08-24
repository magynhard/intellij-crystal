class Outer
  # :nodoc:
  private module SenderReceiverCloseAction
    def self.describe
      "module"
    end
  end

  # :nodoc:
  private enum Mode
    A = 0
    B = 1
  end

  private alias AliasOf = Int32

  def use
    SenderReceiverCloseAction.describe + Mode::A + AliasOf.new(1)
  end
end
