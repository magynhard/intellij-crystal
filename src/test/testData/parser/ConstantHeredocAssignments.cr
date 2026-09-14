module Help
  USAGE = <<-USAGE
    Usage: crystal [command]
  USAGE

  TEMPLATE = <<-TEMPLATE
    Hello #{name}
  TEMPLATE

  COMBINED = <<-ONE + <<-TWO
    one
  ONE
    two
  TWO

  class AfterHeredocs
    def preserved
    end
  end
end
