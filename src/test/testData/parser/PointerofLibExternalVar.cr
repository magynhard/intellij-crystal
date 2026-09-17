module Crystal::FFI
  struct Type
    def self.void
      new(pointerof(LibFFI.ffi_type_void))
    end

    def self.uint8
      new(pointerof(LibFFI.ffi_type_uint8))
    end

    def self.sint64
      new(pointerof(LibFFI.ffi_type_sint64))
    end
  end

  def after_lib_external_var_targets
  end
end
