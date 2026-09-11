lib LibC
  fun strerror_r(Int, Char*, SizeT) : Int
  fun mixed(Int32, output : Char*, LibC::Timeval*)
  fun callback(BioMethod*, (Bio*, Char*, Int) -> Int)
  fun variadic(Char*, ...)
end

class AfterLib
end
