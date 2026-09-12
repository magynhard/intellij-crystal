macro record(name, *properties)
  struct {{name.id}}
    def initialize({{
      properties.map do |field|
        "@#{field.id}".id
      end.splat
    }})
    end
  end
end

class Compiler
  {% for name in names %}
  def {{name.id}}(
    {{operands.splat(", ")}}*, node : ASTNode?
  ) : Nil
  end
  {% end %}
end

def simple({{ items.splat }})
end

def after_splat
end
