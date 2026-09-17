def write_entries(node, last_entry)
  node.entries.each_with_index do |entry, idx|
    write_extra_newlines (last_entry.value || entry.value).end_location, entry.value.location
  end
end

def write_expressions(exps, last_node)
  exps.each do |exp|
    write_extra_newlines (last_node || exp).end_location, exp.location
    writer.write_extra_newlines (last_node || exp).end_location, exp.location
    consume write_extra_newlines (last_node || exp).end_location, exp.location

    write_extra_newlines((last_node || exp).end_location, exp.location)
    writer.write_extra_newlines((last_node || exp).end_location, exp.location)
    consume write_extra_newlines((last_node || exp).end_location, exp.location)
    write_extra_newlines (last_node || exp), exp.location

    write_extra_newlines (last_node || exp).end_location, exp.location do
      exp
    end

    write_extra_newlines (last_node || exp).end_location,
      exp.location,
      true,
  end
end

def after_grouped_arguments
end
