lib LibC
  {% if flag?(:win32) %}
    fun win_only(fd : Int) : Int
  {% end %}

  {% if flag?(:interpreted) %} @[Primitive(:prim)] {% end %}
  fun prim_fn : Int32

  enum ReasonCode
    OK = 0
    {% if flag?(:arm) %}
      FAILURE = 9
    {% end %}
  end

  fun plain : Int32
end

fun trailing : Int32
  1
end
