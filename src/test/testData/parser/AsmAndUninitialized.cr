low = uninitialized UInt32
high = uninitialized UInt32
buffer = uninitialized StaticArray(UInt8, 256)
asm("nop")
asm("rdtsc" : "=a"(low), "=d"(high))
asm("addl $2, $0" : "=r"(result) : "0"(a), "r"(b))
asm("mfence" : : : "memory" : "volatile")
asm("pause" ::: "memory" : "volatile")
asm("cpuid"
  : "=a"(eax), "=b"(ebx), "=c"(ecx), "=d"(edx)
  : "a"(leaf)
  : "memory")
