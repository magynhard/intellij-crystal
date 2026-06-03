# Select Statement Examples (Concurrency)
# Channel-based select for concurrent operations

# Basic select with two channels
ch1 = Channel(String).new
ch2 = Channel(Int32).new

spawn do
  sleep 1
  ch1.send("hello")
end

spawn do
  sleep 2
  ch2.send(42)
end

select
when msg = ch1.receive
  puts "Got string: #{msg}"
when num = ch2.receive
  puts "Got number: #{num}"
end

# Select with timeout
timeout_ch = Channel(Nil).new

spawn do
  sleep 5
  timeout_ch.send(nil)
end

data_ch = Channel(String).new

spawn do
  # Simulate slow operation
  sleep 10
  data_ch.send("data")
end

select
when result = data_ch.receive
  puts "Got data: #{result}"
when timeout_ch.receive
  puts "Timed out!"
end

# Select with send
ch = Channel(Int32).new(1) # buffered channel

select
when ch.send(42)
  puts "Sent successfully"
else
  puts "Channel full, skipping"
end

# Non-blocking select with else
result_ch = Channel(String).new

select
when msg = result_ch.receive
  puts "Got: #{msg}"
else
  puts "Nothing available right now"
end

# Select in a loop (event loop pattern)
tick = Channel(Nil).new
quit = Channel(Nil).new

spawn do
  5.times do
    sleep 0.1
    tick.send(nil)
  end
  quit.send(nil)
end

loop do
  select
  when tick.receive
    puts "tick"
  when quit.receive
    puts "quit"
    break
  end
end

# Fan-in pattern: merge multiple channels
def fan_in(channels : Array(Channel(String))) : Channel(String)
  merged = Channel(String).new

  channels.each do |ch|
    spawn do
      loop do
        merged.send(ch.receive)
      end
    end
  end

  merged
end

# Worker pool with select
def worker_pool(jobs : Channel(Int32), results : Channel(Int32), workers : Int32)
  workers.times do |id|
    spawn do
      loop do
        select
        when job = jobs.receive
          # Process job
          sleep 0.01
          results.send(job * 2)
        end
      end
    end
  end
end

# Select with multiple send/receive combinations
request_ch = Channel(String).new
response_ch = Channel(String).new
error_ch = Channel(Exception).new

spawn do
  select
  when req = request_ch.receive
    response_ch.send("Response to: #{req}")
  end
rescue ex
  error_ch.send(ex)
end

# Bidirectional communication via select
ping = Channel(String).new
pong = Channel(String).new

spawn do
  msg = ping.receive
  pong.send("#{msg} pong")
end

ping.send("ping")
result = pong.receive
puts result # => "ping pong"
