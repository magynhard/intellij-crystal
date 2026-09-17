LibGC.set_start_callback -> do
  GC.lock_write
end

empty = -> do
end

holder = Holder.new(-> do
  GC.lock_write
end)

def after_do_proc_literals
end
