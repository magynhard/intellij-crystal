lib LibC
  fun prep_closure_loc(closure : Closure*, fun : ClosureFun, out : Void*) : Int
  fun set_dll_storage_class(global : ValueRef, class : DLLStorageClass)
  fun build_invoke(then : BasicBlockRef, catch : BasicBlockRef)
end

class AfterLib
end
