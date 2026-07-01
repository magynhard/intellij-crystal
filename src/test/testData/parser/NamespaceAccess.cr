class NamespaceAccess
  def self.outer_method
  end

  class Inner
    def inner_method
    end
  end
end

# Leading :: namespace access
::NamespaceAccess.outer_method

# Nested namespace access
NamespaceAccess::Inner.inner_method

# Multi-level nested namespace access
NamespaceAccess::Inner.inner_method

# Namespace access at call site (without DOT)
x = NamespaceAccess::Inner
