@writers = writers.map(&.as(IO)).to_a
@writers.each(&.write(slice))
@writers.each(&.close) if sync_close?
@writers.each(&.flush)
ptr = pointerof(func).as(self*)
