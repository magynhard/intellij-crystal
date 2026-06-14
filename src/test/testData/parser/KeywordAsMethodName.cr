macro require(namespace, version)
  require {{ "gi-crystal/src/auto/#{namespace.id}" }}
end

def if(x)
  x
end

def self.require(path)
end

macro if(condition)
  {% if condition %}true{% end %}
end

module Foo
  annotation GeneratedWrapper
  end
end

def transfer_array
  slice = ::Bytes.new(ptr, length, read_only: true)
  slice = ::Foo::Bar.new(x)
  slice
end
