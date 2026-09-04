def update(values, index, replacement, enabled)
  values[index] = replacement if enabled
  values[index] += replacement unless enabled
  values[index] = nested = replacement if enabled
  values[index] ||= replacement if enabled
  values[index] &&= replacement unless enabled
  values[index] = replacement if condition = enabled
  values[index][0] = replacement rescue nil
  values[index] = replacement rescue fallback = nil
end

def after_postfix_indexed_assignments
  true
end
