# Backtick commands inside string interpolation (debug/driver.cr)

FILE_CHECK = "FileCheck#{File.basename(`#{__DIR__}/../../src/llvm/ext/find-llvm-config.sh`).lchop("llvm-config")}"

simple = "prefix #{`echo hi`} suffix"

nested = "value #{`printf #{ENV["HOME"]}`} end"

def trailing
  1
end
