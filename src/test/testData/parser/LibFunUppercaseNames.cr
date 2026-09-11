lib LibNative
  fun GetConsoleScreenBufferInfo(handle : Void*, info : INFO*) : Void
  fun BIO_new(BioMethod*) : Bio*
  fun BIO_get_new_index : Int
  fun RtlGenRandom = SystemFunction036(buffer : Void*, length : ULong) : BOOLEAN
  fun Foo = Bar(Int32)
end

class AfterLib
  def preserved
  end
end
