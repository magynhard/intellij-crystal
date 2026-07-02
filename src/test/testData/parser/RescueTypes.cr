# Rescue with all valid Crystal rescue clause forms

begin
  puts "go"
rescue JSON::ParseException
  puts "namespaced type"
rescue e : SomeError
  puts "typed with binding"
rescue SomeError | OtherError
  puts "union types"
rescue e : SomeError | OtherError
  puts "binding with union"
rescue e
  puts "bare binding"
rescue
  puts "bare rescue"
end
