lib LibC
  fun select(nfds : Int, readfds : FdSet*, writefds : FdSet*, exceptfds : FdSet*, timeout : Timeval*) : Int
  fun select = c_select(Int32) : Int
  fun end : Int
  fun plain(value : Int32) : Int32
end

fun select(nfds : Int32) : Int32
  nfds
end

class AfterLib
  def preserved
  end
end
