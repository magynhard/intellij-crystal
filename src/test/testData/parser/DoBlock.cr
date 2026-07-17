def test
  each do |k, v|
    k
  end

  yaml.mapping(reference: self) do
    y
  end

  ExecutionContext.each do |execution_context|
    ec
  end

  parked.synchronize do
    p
  end

  PREOPENS.each do |preopen|
    pre
  end
end

record Key, name : String do
  key
end
