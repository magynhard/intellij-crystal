def masked(value, other)
  value & (other | 1)
end

def capped(size, limit)
  size & (limit)
end

def dot_capped(obj, limit)
  obj.size & (limit)
end

def forwarded(run, blk)
  run &blk
end

def captured(run, blk)
  run(&(blk))
end

def plain_and(a, b)
  a & b
end
