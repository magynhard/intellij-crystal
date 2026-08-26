# Empty blocks are valid Crystal: `{` directly after the callee is a BLOCK,
# never a hash argument. `f 1, { }` mid-list remains a (typeless) hash literal.
Thread.new { }
spawn { }

def f(&block)
  block.call
end

f { }
f do
end
f do end

n = 5
Benchmark.measure do
    n.times do
      spawn { }
    end
end

# chained call after empty brace block
x = f { }.to_s

# empty block via dot-call postfix on a receiver
[1].each { }
[1].map do
end

# non-empty regression: pipes and body still bind as blocks
[1, 2].map { |i| i * 2 }
[1, 2].each_with_index { |v, i| puts v, i }

# hash literals in argument position stay hashes (real Crystal parity)
h = {} of String => Int32
g({"a" => 1})
g(1, {"a" => 2})

def g(a, b)
  nil
end
