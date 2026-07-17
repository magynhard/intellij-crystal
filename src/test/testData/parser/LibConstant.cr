# lib blocks accept SCREAMING_SNAKE constant assignments and CONSTANT-named fields,
# and type_reference array sizes may be CONSTANT (not only INTEGER_LITERAL).
lib LibC
  fun some_fun : Void

  NULL = 0
  TC_IFMT = 0x2200_u32

  struct Timeval
    TV_SEC  : Int64
    TV_USEC : Int64
  end

  struct SockaddrStorage
    SS_FAMILY : Int16
    SS_DATA   : Char[SS_SIZE]
  end
end
