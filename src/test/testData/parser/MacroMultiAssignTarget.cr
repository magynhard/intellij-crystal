# Bare macro-generated multi-assignment targets (`{{operand.var}}, ip = ...`
# in disassembler.cr — verified against the real compiler: `{{a}}, b = 1, 2`
# assigns the generated variable). Macro control tags are transparent, so the
# generated targets parse as structured assignment targets.
def self.disassemble(instructions, ip)
  {% begin %}
    {% for operand in operands %}
      {{operand.var}}, ip = next_instruction instructions, ip, {{operand.type}}
    {% end %}
  {% end %}
end
