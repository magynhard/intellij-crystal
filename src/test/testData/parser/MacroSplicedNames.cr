lib LibLLVM
  {% for target in ALL_TARGETS %}
    fun initialize_{{name}}_target = LLVMInitialize{{target.id}}Target
    fun initialize_{{name}}_target_mc = LLVMInitialize{{target.id}}TargetMC
  {% end %}
end

struct IoUringSqe
  {% for mapping in mappings %}
    def {{mapping.id}}
      {{mapping.id}}
    end

    def {{mapping.id}}=(value)
      value
    end
  {% end %}
end

class Builder
  {% for target in targets %}
    def self.init_{{name}} : Nil
    end
  {% end %}
end

{% if personality %}
  fun {{personality}}(version : Int32) : Int32
    version
  end
{% end %}

enum Errno
  NONE = 0
  {% for value in values %}
    {{value.id}} = LibC::{{value.id}}
  {% end %}
end

fun trailing : Int32
  1
end
