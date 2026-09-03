class ExternalStorageParameters
  def initialize(
    calculation @calculation_time : Time::Span = 5.seconds,
    warmup @warmup_time : Time::Span = 2.seconds,
    verify @expected_crc32 : UInt32? = nil,
  )
    calculation_time
    warmup_time
    expected_crc32
  end

  def named_only(*, at_end @string : String)
    string
  end

  def class_storage(cache @@cache : Int32 = 0, @@direct : Int32 = 1)
    cache
    direct
  end

  def block_storage(&@handler : Proc(String, Nil))
    handler
  end


  def variadic_storage(*@values, **@@options)
    values
    options
  end
end

def declaration_after_external_storage_parameters
end
