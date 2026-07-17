lib LibLLVM
  type TargetDataRef = Void*
  {% for target in ALL_TARGETS %}
    {% name = target.downcase.id %}
    fun initialize_{{name}}_target_info = LLVMInitialize{{target.id}}TargetInfo
    fun initialize_{{name}}_target = LLVMInitialize{{target.id}}Target
  {% end %}
  fun set_module_data_layout = LLVMSetModuleDataLayout(m : ModuleRef, dl : TargetDataRef)
  fun size_of_type_in_bits = LLVMSizeOfTypeInBits(td : TargetDataRef, ty : TypeRef) : ULongLong
end
