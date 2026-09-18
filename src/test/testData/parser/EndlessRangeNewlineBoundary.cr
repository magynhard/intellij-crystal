def shift_editor(indent, shift)
  @editor.update do
    new_indent = (indent + shift).clamp 0..
    @editor.current_line = " " * new_indent + @editor.current_line.lstrip
    @editor.move_cursor_to new_indent
  end
end

def statement_level_endless(stuff)
  first = stuff.size..
  second = stuff.first
  third = stuff.size...
  fourth = stuff.first
  same_line = 1..10
  same_line_exclusive = 1...10
  [first, second, third, fourth, same_line, same_line_exclusive]
end
