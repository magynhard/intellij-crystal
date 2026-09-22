class CLI
  getter root_context : RootContext { RootContext.new(self) }
  property current_context : Context { root_context }
  protected getter cli : CLI
end

class RootContext < Context
  getter results : Array(Result) { [] of Result }
end
