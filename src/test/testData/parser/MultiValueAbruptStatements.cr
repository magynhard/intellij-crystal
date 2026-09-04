def multi_return(flag)
  return 1, "two", value = 3
  return 1,
    2 if flag
  return 1, value = 2 if flag
  return first = second = 1, 2
end

def expression_position(flag)
  flag || return 1, 2
  flag ? (return 3, 4) : nil
  loop { flag || break 5, 6 }
  1.times { flag || next 7, 8 }
end

def multi_break_and_next
  loop do
    break 1,
      "two"
  end

  1.times do
    next value = 1,
      value + 1
  end
end

def heredoc_return
  return <<-FIRST, payload = <<-SECOND
    first
    FIRST
    second
    SECOND
end

def heredoc_break_and_next
  loop do
    break <<-BREAK
      break body
      BREAK
  end

  1.times do
    next <<-NEXT
      next body
      NEXT
  end
end

def declaration_after_multi_value_abrupt_statements
end
