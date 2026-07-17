# A type with arguments used as a method-call receiver.
@cache = Hash(LibXML::Node*, WeakRef(Node)).new

buf = Pointer(LibC::Char*).malloc(1)

# C-binding fun with an external alias that also declares parameters.
lib LibFoo
  fun set_dll = LLVMSetDLL(global : ValueRef, x : Int32)
end
