lib LibC
  fun getch = GetChar
  fun iconv = libiconv(cd : IconvT, inbuf : Char**) : SizeT
  fun realpath = "realpath$DARWIN_EXTSN"(path : Char*) : Char*
  fun tlsv1_method = TLSv1_method : SSLMethod
  fun multiline =
    native_symbol(
      Int32,
      output : Char*,
    ) : Int32
end

class AfterLib
end
