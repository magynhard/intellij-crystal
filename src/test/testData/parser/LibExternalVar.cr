lib LibC
  $errno : Int32
  $environ : UInt8**
  $program_name = "__progname" : UInt8*
  $free = pcre_free : Void* ->
  $stackbottom = GC_stackbottom : Void*
  $select_alias = select : Int32
end

fun trailing : Int32
  1
end
