def try_extend(s, capacity, size)
  if size &+ s.size <= capacity
    extend_unchecked(s)
  end
end

def shift_count(new_len, length)
  count = new_len &- length
  count
end

def tight_wrap(x, y)
  sum = x &+ y
  diff = x &-y
  {sum, diff}
end

def chained_wrap(length, index)
  rindex = length &- index &- 1
  rindex
end

def align_mask(boundary)
  mask = (&-boundary)
  mask
end

def align_step(boundary)
  step = (boundary &- 1)
  step
end
