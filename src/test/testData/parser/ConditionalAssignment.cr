return unless last = other.value.@tail
@tail = fiber if @tail.nil?
tail.list_next = other.value.@head
@tail = last
