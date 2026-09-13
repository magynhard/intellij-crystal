# Word keywords as assignment targets, condition vars, and arguments
# (compiler codegen call.cr `union = ...`, literal_expander.cr `if of = node.of`)

def build
  union = alloca llvm_type(type)
  store @last, union
end

def expand
  if of = node.of
    type_vars = [of.key, of.value] of ASTNode
  end
end

def multi
  of, union = 1, 2
  puts of, union
end

bad_if = 1
