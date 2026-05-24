class Greeter
  def initialize(@name : String)
  end

  def greet
    "Hello, #{@name}!"
  end
end
