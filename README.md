# Scala Command-Line Interface Parser

**SCLIP** is an easy-to-use but powerful command-line interface parser for Scala.
There are [lots](https://github.com/bmjames/scala-optparse-applicative) [of](http://software.clapper.org/argot/)
[CLI](https://github.com/scopt/scopt) [parsers](https://github.com/scallop/scallop), with
varying numbers of features, so why roll my own? Because I really, really value *ease-of-use*.
As shown in the examples below, SCLIP has an intuitive, thread-safe API, while containing all the features
you need to work with command-line interfaces.

SCLIP was inspired by [Scallop](https://github.com/scallop/scallop).

## Using SCLIP

Here's a simple, fully functional example:

```scala
import org.nbrahms.sclip._

class Options(args: Array[String]) extends Clip(args) {
  // All options are declared as vals
  val port = dopt("port", 80)     // Optional, defaults to 80
  val host = ropt[String]("host") // Required
}
object Application {
  def main(args: Array[String]) {
    val options = new Options(args)
    println(s"Connecting to ${options.host}:${options.port}")
  }
}
```
```bash
$ java Application --host 127.0.0.1
Connecting to 127.0.0.1:80
```

You can run this example (with correct classpath), by running `bin/launch-test.sh` after compilation.

That's all you need to get going, but it has a lot more.

## Full documentation

See the full [0.2.2 API documentation](http://nbrahms.github.io/sclip/doc/0.2.2/index.html#org.nbrahms.sclip.package).

## Examples

* Required, default-valued, and monadic options

```scala
val intOpt: Option[Int] = opt("x")
val intDefault: Int = dopt("y", 10)
val intRequired: Int = ropt("z")
```

* Repeated option arguments

```scala
val seq = sopt[Int]("seq")       // parses "--seq 1 2 3 ..." as Seq(1, 2, 3)
```

* Key-value pairs

```scala
val map = kv[Int]("map")         // parses "--map a=1,b=2" or "--map a=1 b=2" as Map("a" -> 1, "b" -> 2)
```

* Trailing arguments

```scala
tail[Int]                        // parses first trailing argument as Int
tail[Seq[File]]                  // parses remaining trailing arguments as File objects
```

* Automatically parses primitives, sequences, tuples and case classes

```scala
case class Pixel(x: Int, y: Int)
val pixel = opt[Pixel]("pixel")  // parses "--pixel 123 456" as Pixel(123, 456)
```

* Easily extensible parsing

```scala
import org.apache.commons.codec.binary.Hex
implicit val hexParser = new UnaryParser[Seq[Byte]](Hex.decodeHex(_).toSeq, "hex")
val hash = opt[Seq[Byte]]("md5") // parses "--md5 d131dd02c5e6eec4" as a byte seq
```

* Both long and short options

```bash
$ java Application --host 127.0.0.1 -p 456
```

```scala
val noShort = opt[Int]("noShort", hasShort = false) // Does not parse "-n"
val onlyShort = opt[Int]("O")                       // Parses "-O", but not "--O"
val withChar = opt[Int]("user", shortChar = 'U')    // Parses "--user" or "-U'
```

You can also disable short option forms by default:
```scala
class Options(args: Seq[String]) extends Clip(args, NoDefaultShort) {
  val noShort = opt[Int]("noShort")
  val onlyShort = opt[Int]("O")
  val withShort = opt[Int]("withShort", hasShort = true, shortChar = 's')
}
```

* Flags and grouped flags

```scala
// parses "-lht", "-l -h", etc.
val humanReadable = flag("h")
val timeSorted = flag("t")
val longForm = flat("l")
```

* Automated error checking for unrecognized options, repeated arguments, and extra arguments

```scala
class Options extends Clip(args) {
  ...
  check(Unrecognized, NoRepeated, NoLeading)   // Must go after your options
}
```

* Auto-generated help:

```scala
class Options extends Clip(args) {
  val isAsync = flag("async", desc = "If present, all requests are executed asynchronously")
  val host = ropt[String]("host", desc = "Host address")
  val port = dopt("port", 80, desc = "Host port, or 80 if missing")
  check(AutoHelp)
}
```
```
$ java Application --help
Exception in thread "main" org.nbrahms.sclip.package$HelpCalled: --help called:
Usage:
  [flags] --host string1 [options]

  --host|-h string1
      Host address

Flags:
  --async|-a
      If present, all requests are executed asynchronously

Options:
  --port|-p int1
      Host port, or 80 if missing
    ...
```

* Immutable and thread-safe

* Nestable

```scala

abstract class BaseApplication {
  class Options(args: Seq[String]) extends Clip(args) {
    val host = ropt[String]("host")
    val port = dopt("port", 80)
  }
  protected var baseOptions = _
  def setup(args: Seq[String]) = baseOptions = new Options(args)
}
object Application extends BaseApplication {
  class Options(args: Seq[String]) extends Clip(args) {
    val user = ropt[String]("user")
  }
  def main(args: Array[String]) {
    setup(args)
    val appOptions = new Options(baseOptions.remaining)
    ...
  }
}

```

For more examples, see the [test spec](test-src/SclipSpec.scala)

## Installation

Add this to your build.sbt:

```
resolvers += "sclip" at "https://raw.githubusercontent.com/nbrahms/sclip/release/releases/"

libraryDependencies += "org.nbrahms" %% "sclip" % "0.2.3"
```

SCLIP is built on Scala 2.10.5 and 2.11.5, and should be compatible with 2.10.4 or 2.11.2 and higher.
