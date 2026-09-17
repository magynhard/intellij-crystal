def inspect_with_backtrace(backtrace)
  backtrace.try &.each do |frame|
    puts frame
  end
end

plain_shorthand = list.map &.to_s
filtered = list.select &.even? { |x| x }

def after_proc_shorthand_block
end
