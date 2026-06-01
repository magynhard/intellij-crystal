def each(& : K, V ->)
  yield @key, @value
end
def transform(& : K, V -> U) forall U
end
