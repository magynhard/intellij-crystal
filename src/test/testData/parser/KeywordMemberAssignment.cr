class ThreadList
  def push(node : Node) : Nil
    @mutex.synchronize do
      node.previous = nil

      if tail = @tail
        node.previous = tail
        @tail = tail.next = node
      else
        @head = @tail = node
      end
    end
  end

  def refresh(node : Node) : Nil
    node.next = nil
    @tail = tail.link = node
  end

  def each_node(node : Node) : Nil
    while node
      yield node.next
      node = node.next
    end
  end

  def after_keyword_setter
  end
end
