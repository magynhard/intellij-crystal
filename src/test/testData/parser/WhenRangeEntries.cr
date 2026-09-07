diff = Time.utc - date.to_utc

past = case diff
       when 0.seconds..1.day then "today is the day!"
       when 1.day..2.days    then "1 day past"
       else                       "#{diff.total_days.to_i} days past"
       end

range_arg = f 1..2
line_range = lines[a - 1...b]
