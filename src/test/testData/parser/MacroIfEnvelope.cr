class ElfCheck
  def valid?(header : Header) : Bool
    header.ei_class == {% if flag?(:bits64) %} LibELF::CLASS_64 {% else %} LibELF::CLASS_32 {% end %} &&
      header.ei_data == {% if big_endian? %} LibELF::ENDIAN_BIG {% else %} LibELF::ENDIAN_LITTLE {% end %}
  end
end

expect_raises({% if flag?(:win32) %} File::BadExecutableError {% else %} File::AccessDeniedError {% end %}, "msg") do
end

def self.status(code : Int32) : UInt32
  new(system_exit_status: {% if flag?(:unix) %}
    code << 8
  {% else %}
    code.to_u32!
  {% end %})
end

def close(io : IO) : Nil
  begin
    io.close
  rescue IO::Error{% unless flag?(:x) %} | ArgumentError{% end %}
  end
end

def trailing : Int32
  1
end

expect_raises({% if flag?(:win32) %} IO::Error, "The parameter is incorrect" {% else %} File::NotFoundError{% end %}) do
  Process.run("")
end

consume({% if flag %} A, B {% elsif other %} C, D {% else %} E {% end %})
