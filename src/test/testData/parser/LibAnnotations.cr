lib LibC
  @[Flags]
  enum FlockOp
    SH = 0x1
    EX = 0x2
  end

  @[Packed]
  struct EpollEvent
    events : UInt32
  end

  @[ReturnsTwice]
  fun fork : PidT

  @[Raises]
  fun risky : Int32

  fun plain : Int32
end

fun trailing : Int32
  1
end
