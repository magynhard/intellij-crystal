class PointerHolder
  property stack_top : Void*
  getter ip : Void*, count : Int32
  property lastmatch : UInt8* = Pointer(UInt8).null
  property direct : Int32**
  property maybe : Int32*?
  class_getter cached : Int32* do
    Pointer(Int32).null
  end
end

record FromCharsResult(U), ptr : U*, ec : Int32

def consume(buffer : UInt8*)
  buffer
end

consume(x : UInt8*)
consume(x : UInt8* | Nil)
consume(x : UInt8**)
consume(x : UInt8***)
consume(x : UInt8***? | Nil)
consume(
  x : UInt8*
)
consume(x : UInt8*[4])
consume(x : UInt8* -> Nil)
consume(x : UInt8* = Pointer(UInt8).null)
consume(level: ::Socket::Protocol::TCP)
consume(value: ::Socket::Protocol::TCP || nil)
consume(value: ::Socket::Protocol::TCP ? 1 : 2)
consume level: ::Socket::Protocol::TCP
consume value: ::Socket::Protocol::TCP || nil
consume value: ::Socket::Protocol::TCP ? 1 : 2
consume(x \
  : UInt8*)
consume(x\
: UInt8*)
consume(tab	: UInt8*)
consume(formfeed: UInt8*)
consume(x : Int32 = y = 1)

def multiply(a, b)
  a * b
end

multiply(x: 2 * 3)
multiply x: 2 * 3
multiply(x: a * -b)
multiply(x: a * (b + c))
multiply x: a *
  b
multiply(x: PointerSize *
  3)
multiply x: PointerSize *
  3

class ExtraPointerShapes
  property triple : UInt8***
  property array : UInt8*[4]
  property callback : UInt8* -> Nil
  property proc_pointer : (Int32 -> Int32)*
  property nilable_proc_pointer : (Int32 -> Int32)*?
  property nilable_array : UInt8*[4]?
  property symbolic_array_pointer : UInt8[BUFFER_SIZE]*
  property pointer_metaclass : Int32*.class
  property union_metaclass : (Int32 | String).class
  property semi : UInt8*; getter after_semi : Int32
  getter last : Int32*
end

class DeclarationAfterPointerTypeArguments
end
