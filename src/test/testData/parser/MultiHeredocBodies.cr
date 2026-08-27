def example_spec
  assert_finding(rule, <<-CRYSTAL, <<-BOHNE, <<-ZITRONE, <<-MELONE)
    def process(id : Int32) : String
      raise ArgumentError.new("invalid id")
    end
  CRYSTAL
    def some more
      raise
    end
  BOHNE
    def some one

    end
  ZITRONE
    def coko

    end
  MELONE
end
