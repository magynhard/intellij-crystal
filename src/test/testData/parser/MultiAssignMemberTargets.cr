class Reader
  def read_next
    @editor.width, @editor.height = Term::Size.size

    a.foo, a.bar = 1, 2
    *a.foo, a.bar = 1
    a.foo, *a.bar = 1

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
end
