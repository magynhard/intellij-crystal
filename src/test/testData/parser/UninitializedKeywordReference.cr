# `uninitialized` is a keyword-named parameter when no type follows.

def declare_class_var(node, var, uninitialized)
  TypeDeclarationWithLocation.new(
    var_type.virtual_type,
    node.location.not_nil!,
    uninitialized,
    nil,
  )
end
