if true
  require "./conditional"
end

class Loader
  require "./type_dependency"
end

1.times do
  require "./block_dependency"
end

def load
  require "./method_dependency"
end

fun native_load
  require "./fun_dependency"
end

dependency = require "./assignment_dependency"

load(require "./argument_dependency")

if require "./condition_dependency"
end

load require "./bare_argument_dependency"

require "./postfix_dependency" if true

require "./or_dependency" || fallback

message = "loaded: #{require "./interpolated_dependency"}"

{{ require "./macro_dependency" }}

postfix_message = "loaded: #{require "./interpolated_postfix" if true}"

{{ require "./macro_postfix" if true }}

fallback + require "./right_operand_dependency"
