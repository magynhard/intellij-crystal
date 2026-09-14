lib LibC
  struct NS
    next : NS*
    type : Int32
    include Common
  end

  union EventData
    alias : AliasEvent
    union : UnionEvent
    include : Int32
  end

  struct Color
    r, g, b, unused : UInt8
    x, y : Int16
    alpha,
      opacity : UInt8
  end

  struct Generated
    {% if flag?(:win32) %}
      handle : Void*
    {% end %}
  end

  fun after_aggregates : Int32
end

class AfterLib
end
