class Reader
  def read_next
    @editor.width, @editor.height = Term::Size.size

    a.foo, a.bar = 1, 2
    *a.foo, a.bar = 1
    a.foo, *a.bar = 1

    # Index and self targets (pointer.cr swap, slice/sort.cr, bcrypt.cr).
    self[i], self[j] = self[j], self[i]
    a.value, a[n] = a[n], a.value
    x, v[0] = v[0], x
    cdata[i], cdata[i + 1] = l, r
    self.current_pos, @line_number, @column_number = old_pos, old_line, old_column

    # Structural variants of the indexed targets.
    *a[i], b = values
    a[i], *b[j] = values
    a[i][j], b = 1, 2
    a.value[i], b = 1, 2
    Foo::Bar[i, j], b = 1, 2
    a[i += 1], b = 1, 2
    u, v = v, u if u > v

    loop do
      case read
      in Char then on_char(read)
      in String then on_string(read)
      in .home?, .ctrl_a? then on_begin
      end
    end
  end

  def after_assignments
  end

  def check(value, list, limit)
    case
    when list.empty?, value < limit
      value
    end
  end

  # PEG fallback canary: a comma-separated expression list without `=`
  # must keep binding as plain `when` conditions, never as multi-assignment.
  def indexed_expression_list_fallback(a, i, j)
    case
    when a[i], a[j]
      true
    end
  end
end
