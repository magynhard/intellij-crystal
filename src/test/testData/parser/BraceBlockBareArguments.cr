def assert_prints(actual, expected, file, line)
end

assert_prints JSON.build { |json| with json yield json }, expected, file: file, line: line

assert_prints YAML.build { |yaml| with yaml yield yaml }, expected, file: file, line: line

def collect(stream)
  stream
end

mapped = items.map { |e| e }
