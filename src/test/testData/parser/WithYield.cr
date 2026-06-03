# with...yield Block Examples
# Changing the scope/receiver within a block

# Basic with...yield
class HtmlBuilder
  @buffer = IO::Memory.new

  def tag(name : String)
    @buffer << "<#{name}>"
    with self yield
    @buffer << "</#{name}>"
  end

  def text(content : String)
    @buffer << content
  end

  def to_s : String
    @buffer.to_s
  end
end

html = HtmlBuilder.new
html.tag("div") do
  tag("p") do      # 'self' is html inside the block
    text("Hello!")
  end
end

# DSL with nested with...yield
class Router
  @routes = [] of {String, String, Proc(Nil)}

  def get(path : String, &block : ->)
    @routes << {"GET", path, block}
  end

  def post(path : String, &block : ->)
    @routes << {"POST", path, block}
  end

  def group(prefix : String)
    with Group.new(prefix, self) yield
  end

  class Group
    def initialize(@prefix : String, @router : Router)
    end

    def get(path : String, &block : ->)
      @router.get("#{@prefix}#{path}", &block)
    end

    def post(path : String, &block : ->)
      @router.post("#{@prefix}#{path}", &block)
    end
  end
end

router = Router.new
router.group("/api/v1") do
  get("/users") { }
  post("/users") { }
  get("/posts") { }
end

# Configuration DSL with with...yield
class ServerConfig
  property host : String = "localhost"
  property port : Int32 = 8080
  property workers : Int32 = 4
  property? ssl : Bool = false

  def ssl
    with SslConfig.new yield
  end

  class SslConfig
    property cert_path : String = ""
    property key_path : String = ""
  end
end

def configure_server
  config = ServerConfig.new
  with config yield
  config
end

config = configure_server do
  self.host = "0.0.0.0"
  self.port = 443
  self.ssl = true
end

# Form builder with with...yield
class FormBuilder
  @fields = [] of String

  def input(name : String, type : String = "text")
    @fields << "<input name=\"#{name}\" type=\"#{type}\">"
  end

  def select(name : String)
    with SelectBuilder.new(name) yield
  end

  def textarea(name : String, content : String = "")
    @fields << "<textarea name=\"#{name}\">#{content}</textarea>"
  end

  class SelectBuilder
    def initialize(@name : String)
      @options = [] of String
    end

    def option(value : String, label : String = value)
      @options << "<option value=\"#{value}\">#{label}</option>"
    end
  end
end

def form(action : String)
  builder = FormBuilder.new
  with builder yield
  builder
end

form("/submit") do
  input("name")
  input("email", type: "email")
  select("country") do
    option("de", "Germany")
    option("us", "United States")
  end
  textarea("message")
end

# Spec-style DSL using with...yield
class Spec
  def describe(what : String)
    with DescribeBlock.new(what) yield
  end

  class DescribeBlock
    def initialize(@what : String)
    end

    def it(description : String, &block : ->)
      puts "  it #{description}"
      block.call
    end

    def before_each(&block : ->)
      @before = block
    end

    def context(description : String)
      with ContextBlock.new(description) yield
    end
  end

  class ContextBlock
    def initialize(@description : String)
    end

    def it(description : String, &block : ->)
      puts "    it #{description}"
      block.call
    end
  end
end

spec = Spec.new
spec.describe("Array") do
  it("has size") { }
  context("when empty") do
    it("returns 0 for size") { }
  end
end
