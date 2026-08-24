# Complex expression default
def configure(
  host : String = ENV.fetch("HOST", "localhost"),
  port : Int32 = ENV.fetch("PORT", "3000").to_i,
  workers : Int32 = System.cpu_count.to_i,
  debug : Bool = ENV.has_key?("DEBUG")
)
  {host: host, port: port, workers: workers, debug: debug}
end

def mixed(
  first : Int32 = base.count,
  second : String = base.label,
)
  {first: first, second: second}
end
