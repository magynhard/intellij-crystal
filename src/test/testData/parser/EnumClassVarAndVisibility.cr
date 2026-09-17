module LLVM
  enum Attribute : UInt64
    Alignment
    Captures

    @@kind_ids = nil.as(Hash(Attribute, UInt32)?)

    protected def self.kind_ids
      @@kind_ids ||= load_llvm_kinds_from_names
    end

    private def self.typed_attrs
      @@typed_attrs ||= load_llvm_typed_attributes
    end

    def each_kind(& : UInt32 ->)
      yield 1u32
    end

    ZExt
  end

  def after_enum_members
  end
end
