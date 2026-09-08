encoded.headers["Etag"].should eq Kemal::Utils.etag_with_coding(identity.headers["Etag"], "gzip")

annotated_source = AnnotatedSource.new [] of String, [
  {2, "", "Annotation C"},
]

first = config.foo["key"]
