# Lib with type alias and alias in lib blocks

lib LibMyLib
  fun calculate(a : Int32, b : Int32) : Int32
  fun process(callback : (Int32 -> Void)) : Void

  type Handle = Void*
  alias ErrorCode = Int32

  enum Flags
    None    = 0
    Verbose = 1
    Debug   = 2
  end
end
