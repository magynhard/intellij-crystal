def transform(& : T -> U) : Container(U) forall U
  Container(U).new(yield @value)
end
