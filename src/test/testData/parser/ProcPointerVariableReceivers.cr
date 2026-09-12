ivar_pointer = ->@worker.run
cvar_pointer = ->@@worker.run
typed_pointer = ->@worker.run(Int32)
fiber = Fiber.new(name: "stack-pool-collector", &->@stack_pool.collect_loop)

def after_pointer
end
