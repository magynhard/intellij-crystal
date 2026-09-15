macro disassemble(instruction)
  {% for operand in instruction[:operands] || [] of Nil %}
    {{operand.var}}, ip = next_instruction instructions, ip, {{operand.type}}
  {% end %}
end
