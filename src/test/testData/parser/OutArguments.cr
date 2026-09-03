class OutArgumentExamples
  def exercise
    receive(out local)
    receive(out @instance)
    receive(out _)
    receive(out
      multiline)
    receive(1, out middle, 2)

    receive out bare_local
    receive out @bare_instance
    receive 1, out bare_middle, 2
    receive out chained_target .result

    receive(target: out named_local)
    receive(target: out @named_instance)
    receive(target:
      out multiline_named)
    receive target: out bare_named_local
    receive target: out @bare_named_instance
    receive target:
      out bare_multiline_named
    receive target: out named_chained_target .result

    receive(out: value)
    receive out: value
  end
end

def declaration_after_out_arguments
end
