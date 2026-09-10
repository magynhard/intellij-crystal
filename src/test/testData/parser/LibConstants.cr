require "./unistd"

lib LibC
  F_GETFD    =         1
  F_SETFD    =         2
  O_CLOEXEC  = 0o2000000
  AT_FDCWD   =      -100
  READ_WRITE = 1 | 2
  FORWARDED  = LibC::F_GETFD

  struct Flock
    l_type : Short
    l_start : OffT
  end

  fun fcntl(fd : Int, cmd : Int, ...) : Int
end

class AfterLib
  def preserved
  end
end
