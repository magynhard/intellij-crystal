# Loop examples
loop do
  break
end

loop do
  loop do
    break
  end
  break
end

def process_queue(queue)
  loop do
    item = queue.pop?
    break unless item
    process(item)
  end
end
