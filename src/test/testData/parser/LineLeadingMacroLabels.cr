module Crystalline::Lightweight
  record MethodInfo,
    name : String,
    class_method : Bool = false,
    macro : Bool = false,
    doc : String? = nil,
    name_size : Int32 = 0

  record(ParenthesizedInfo,
    macro : Bool = false,
    doc : String? = nil)

  configure(
    macro: false,
    doc: nil)

  private macro build(name)
    def {{ name.id }}
    end
  end

  class DeclarationAfterLabels
    def intact
    end
  end
end
