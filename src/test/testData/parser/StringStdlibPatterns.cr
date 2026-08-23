{% if compare_versions(Crystal::VERSION, "1.16.0") >= 0 %}
  {%
    table = (0...256).map { -1 }
    (0...10).each do |i|
      table[48 + i] = i
    end
  %}

  # :nodoc:
  CHAR_TO_DIGIT = Slice(Int8).literal({{ table.splat }})
{% else %}
  # :nodoc:
  CHAR_TO_DIGIT = begin
    table = StaticArray(Int8, 256).new(-1_i8)
    table
  end
{% end %}

class String
  def %(other) : String
    sprintf self, other
  end

  def ==(other : self) : Bool
    return false if @length != other.@length && @length != 0 && other.@length != 0
    to_unsafe.memcmp(other.to_unsafe, bytesize) == 0
  end

  def encode(slice, from, to, io, invalid)
    outbuf = uninitialized UInt8[1024]

    Crystal::Iconv.new(from, to, invalid) do |iconv|
      while inbytesleft > 0
        err = iconv.convert(Pointer(UInt8*).null, pointerof(inbuf_ptr), pointerof(outbuf_ptr))
        if err == Crystal::Iconv::ERROR
          iconv.handle_invalid(pointerof(inbuf_ptr), pointerof(inbytesleft))
        end
      end
    end
  end

  def hexbytes : Bytes
    hexbytes? || raise(ArgumentError.new("not a hexstring"))
  end

  private def just(len, char, justify)
    return self if size >= len

    case justify
    when .< 0
      leftpadding, rightpadding = 0, padding
    when .> 0
      leftpadding, rightpadding = padding, 0
    else
      leftpadding = padding // 2
    end

    match.try &.begin(0)
    chars[index] = carry = 'a'
  end

  record ToUnsignedInfo(T),
    value : T,
    negative : Bool,
    invalid : Bool

  {% begin %}
    {% for type, type_index in U %}
      other{{type_index}} = others[{{type_index}}]
    {% end %}
  {% end %}

  def upcase(options : Unicode::CaseOptions = :none) : String
    self
  end

  def downcase(options : Unicode::CaseOptions = :none) : String
    self
  end
end
