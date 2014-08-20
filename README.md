# Sbt-web JsAppliction Plugin 

This module based on google clouser compiler allows you to compile set of js files in one and make minification

jsappliction file example

```
files =  [
        ${testFiles}".js"
        //"templates/template1.js"
]


options = {
    compilationLevel = "WHITESPACE"
}
```

File format is based on [Typesafe Config](https://github.com/typesafehub/config).

## Installation

Add a dependency to the plugins.sbt file:

```scala
addSbtPlugin("com.github.germanosin.sbt" % "sbt-jsappliction" % "1.0.0")
```

## Author

German Osin