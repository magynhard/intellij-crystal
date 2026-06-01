x = ->{ 42 }
y = ->(a : Int32) { a + 1 }
method_ref = ->double(Int32)
instance_ref = ->calc.add(Int32, Int32)
def with_proc_literal(func : Proc(Int32, Int32) = ->(x : Int32) { x })
end
def simple_default(x : Int32 = 42)
end
