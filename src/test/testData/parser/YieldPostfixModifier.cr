def self.timer(label : String, measure_time? : Bool)
  return yield unless measure_time?

  start_time = Time.utc
  yield

  puts "#{label}: #{(Time.utc - start_time).total_milliseconds}ms"
end

def collect(flag : Bool)
  items = yield if flag
  yield(if flag then 1 else 2 end)
end

while true
  break yield unless done?
  next yield if skip?
end
