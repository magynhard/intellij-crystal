require "http/client"
require "uri"
require "json"
require "openssl"

s = 123

t = "lols"


a = 1

b = [
  1,
  2,
  3,
  [
    1,
    2,
    3
  ],
  4
]


require "./gi-crystal/closure_data_manager"
require "./gi-crystal/toggle_ref_manager"

module GICrystal
  macro require(namespace, version)
    {% req_path = "#{__DIR__}/auto/#{namespace.underscore.id}-#{version.underscore.id}/#{namespace.underscore.id}.cr" %}
    {% unless file_exists?(req_path) %}
      {{ raise "Bindings for #{namespace.id}-#{version.id} not yet generated, run ./bin/gi-crystal first." }}
    {% end %}
    require {{ "gi-crystal/src/auto/#{namespace.underscore.id}-#{version.id}/#{namespace.underscore.id}" }}
  end

  annotation GeneratedWrapper
  end

  enum Transfer
    None
    Container
    Full
  end

  INSTANCE_QDATA_KEY = LibGLib.g_quark_from_static_string("gi-crystal::instance")
  INSTANCE_FACTORY = LibGLib.g_quark_from_static_string("gi-crystal::factory")

  class ObjectCollectedError < RuntimeError
  end

  def instance_pointer(object) : Pointer(Void)
    {% raise "Implement GICrystal.instance_pointer(object) for your fundamental type." %}
  end

  def finalize_instance(object)
    {% raise "Implement GICrystal.finalize_instance(object) for your fundamental type." %}
  end

  @[AlwaysInline]
  def to_bool(value : Int32) : Bool
    value != 0
  end

  @[AlwaysInline]
  def to_c_bool(value : Bool) : Int32
    value ? 1 : 0
  end

  @[AlwaysInline]
  def ref(null : Nil) : Nil
  end

  def transfer_null_ended_array(ptr : Pointer(Pointer(UInt8)), transfer : Transfer) : Array(String)
    res = Array(String).new
    return res if ptr.null?

    item_ptr = ptr
    while !item_ptr.value.null?
      res << String.new(item_ptr.value)
      LibGLib.g_free(item_ptr.value) if transfer.full?
      item_ptr += 1
    end
    LibGLib.g_free(ptr) unless transfer.none?
    res
  end

  def transfer_array(ptr : Pointer(Pointer(UInt8)), length : Int, transfer : Transfer) : Array(String)
    res = Array(String).new(length)
    return res if ptr.null?

    length.times do |i|
      item_ptr = (ptr + i).value
      res << String.new(item_ptr)
      LibGLib.g_free(item_ptr) if transfer.full?
    end
    LibGLib.g_free(ptr) unless transfer.none?
    res
  end

  def transfer_array(ptr : Pointer(UInt8), length : Int, transfer : Transfer) : ::Bytes
    slice = ::Bytes.new(ptr, length, read_only: true)
    if transfer.full?
      slice = slice.clone
      LibGLib.g_free(ptr)
    end
    slice
  end

  def transfer_array(ptr : Pointer(T), length : Int, transfer : Transfer) : Array(T) forall T
    Array(T).build(length) do |buffer|
      ptr.copy_to(buffer, length)
      length
    end
  ensure
    LibGLib.g_free(ptr) if transfer.full?
  end

  def transfer_full(str : Pointer(UInt8)) : String
    String.new(str).tap do
      LibGLib.g_free(str)
    end
  end

  macro declare_new_method(type, qdata_get_func)
    def self.new(pointer : Pointer(Void), transfer : GICrystal::Transfer) : self
      instance = LibGObject.{{ qdata_get_func }}(pointer, GICrystal::INSTANCE_QDATA_KEY)
      return instance.as({{ type }}) if instance

      instance_g_type = pointer.as(LibGObject::TypeInstance*).value.g_class.value.g_type
      if instance_g_type != g_type
        ctor_ptr = LibGObject.g_type_get_qdata(instance_g_type, GICrystal::INSTANCE_FACTORY)
        if ctor_ptr
          ctor = Proc(Void*, GICrystal::Transfer, {{ type }}).new(ctor_ptr, Pointer(Void).null)
          return ctor.call(pointer, transfer)
        end
      end

      instance = {{ type }}.allocate
      instance.initialize(pointer, transfer)
      GC.add_finalizer(instance)
      instance
    end
  end

  extend self
end
