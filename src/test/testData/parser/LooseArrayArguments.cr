opts = CLI.parse_args [
  Path[Dir.tempdir, "foo.cr"].to_s,
  Path[Dir.tempdir, "bar.cr"].to_s,
]

b = f [
  1,
  2,
]

tight = config.foo["key"]
index = h.values[0]
