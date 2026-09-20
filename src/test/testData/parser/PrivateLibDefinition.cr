# Visibility-modified lib definitions (pointer_spec.cr)

private lib LibPointerSpec
  type A = Void
  type B = Void
end

protected lib LibSecond
  fun size : Int32
end

class Container
  private lib LibNested
    fun reset
  end

  private def helper
  end
end
