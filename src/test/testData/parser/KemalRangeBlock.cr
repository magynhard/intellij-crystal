private def parse_ranges(range_header : String?, file_size : Int64) : Array({Int64, Int64})
  return [] of {Int64, Int64} unless range_header

  ranges = [] of {Int64, Int64}
  return ranges unless range_header.starts_with?("bytes=")

  max_ranges = Kemal.config.max_ranges
  requested_bytes = 0_i64
  parts = 0

  range_header[6..].split(",") do |range|
    parts += 1
    return [] of {Int64, Int64} if parts > max_ranges

    if match = range.match /(\d{1,})-(\d{0,})/
      startb = match[1].to_i64 { 0_i64 }
      endb = match[2].to_i64 { 0_i64 }
      endb = file_size - 1 if endb == 0

      if startb < endb && endb < file_size
        requested_bytes += 1_i64 + endb - startb
        return [] of {Int64, Int64} if requested_bytes > file_size
        ranges << {startb, endb}
      end
    end
  end

  ranges
end
