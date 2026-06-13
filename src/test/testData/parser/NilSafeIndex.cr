# Nil-safe index access using ? suffix

value = [1, 2, 3][4]?

hash = {"a" => 1, "b" => 2}["c"]?

nested = [[1, 2], [3, 4]][0]?[1]?

# Nil-safe index inside each block with if condition
arr = [1, 2, 3]
arr.each do |item|
  if item["states"]?
    puts 1
  else
    puts 2
  end
end
