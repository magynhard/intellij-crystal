private record Binding, source : String, level : Severity
private enum DefArgType
  NONE
  SPLAT
end
protected getter cache : Hash(LibXML::Node*, WeakRef(Node))
private getter x : Int32
