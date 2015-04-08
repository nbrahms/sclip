package org.nbrahms.sclip

import org.scalatest.{Matchers, WordSpec}

class SclipSpec extends WordSpec with Matchers {
  case class Pixel(x: Int, y: Int)
  class ClipTest(cl: String, behaviors: Behavior*) extends Clip(cl.split("\\s+"), behaviors: _*) {
    val byte = opt[Byte]("byte")
    val int = opt[Int]("int")
    val double = opt[Double]("double", shortChar = 'D')
    val seq = sopt[Int]("seq")
    val map = kv[Int]("map")
    val tuple = opt[(String, Double)]("tuple", hasShort = false)
    val pixel = opt[Pixel]("pixel", hasShort = false)
    val floatWithDefault = dopt("float", 1.0F)
    val flag: Boolean = flag("xflag")
    val flag2: Boolean = flag("yflag")
    val flag3: Boolean = flag("z", hasShort = true)
  }
  object ClipTest {
    def apply(args: String) = new ClipTest(args)
  }
  "sclip" when {
    "using opts" should {
      "parse using long names" in {
        ClipTest("--byte 1").byte should be (Some(1.toByte))
        ClipTest("--int 2").int should be (Some(2))
        ClipTest("--double 3.0").double should be (Some(3.0))
      }
      "parse using short names" in {
        ClipTest("-i 2").int should be (Some(2))
      }
      "parse using alternate short names" in {
        ClipTest("-D 5.3").double should be (Some(5.3))
      }
      "parse negative numbers" in {
        ClipTest("-i -1").int should be (Some(-1))
      }
      "parse tuples" in {
        ClipTest("--tuple a 3.0").tuple should be (Some("a", 3.0))
      }
      "parse case classes" in {
        ClipTest("--pixel 123 456").pixel should be (Some(Pixel(123, 456)))
      }
      "yield None for missing opts" in {
        ClipTest("").byte should be (None)
        ClipTest("-i 2").byte should be (None)
      }
    }
    "using dopts" should {
      "parse present dopts" in {
        ClipTest("--float 4.0").floatWithDefault should be (4.0F)
      }
      "yield default values for missing dopts" in {
        ClipTest("").floatWithDefault should be (1.0F)
      }
    }
    "using ropts" should {
      "pass with present arguments" in {
        new ClipTest("--req 5"){ val req = ropt[Int]("req") }.req should be (5)
      }
      "fail with missing arguments" in {
        an[IllegalArgumentException] should be thrownBy {
          new ClipTest(""){ val req = ropt[Int]("req") }
        }
      }
    }
    "using sopts" should {
      "parse sequences" in {
        ClipTest("--seq 1 2 3").seq should be (Seq(1, 2, 3))
        ClipTest("-i 2 --seq 1 2 3 --byte 1").seq should be (Seq(1, 2, 3))
        ClipTest("--seq 1 2 3 -i 2").seq should be (Seq(1, 2, 3))
      }
      "parse negative numbers by default" in {
        ClipTest("--seq 1 -4 5").seq should be (Seq(1, -4, 5))
        new ClipTest("--seq 1 0 -1") { val one = flag("1") }.one should be (false)
      }
      "not parse negative numbers with StopSequenceOnNegative" in {
        new ClipTest("--seq 1 0 -1", StopSequenceOnNegative).seq should be (Seq(1, 0))
        new ClipTest("--seq 1 0 -1", StopSequenceOnNegative) { val one = flag("1") }.one should be (true)
      }
    }
    "using flags" should {
      "yield true for present long flags" in {
        ClipTest("--xflag").flag should be (true)
      }
      "yield true for present short flags" in {
        ClipTest("-x").flag should be (true)
      }
      "yield false for missing flags" in {
        ClipTest("-g").flag should be (false)
      }
      "parse flag groups" in {
        ClipTest("-xy").flag should be (true)
        ClipTest("-xy").flag2 should be (true)
      }
    }
    "using key-value pairs" when {
      "using long option" should {
        "parse empty" in {
          ClipTest("--map").map should be ('empty)
        }
        "parse space-separated" in {
          ClipTest("--map a=1 b=2").map should be (Map("a" -> 1, "b" -> 2))
        }
        "parse comma-separated" in {
          ClipTest("--map a=1,b=2").map should be (Map("a" -> 1, "b" -> 2))
        }
        "parse mixed" in {
          ClipTest("--map a=1,b=2 c=3").map should be (Map("a" -> 1, "b" -> 2, "c" -> 3))
        }
        "parse with additional options" in {
          ClipTest("--map a=1 -i 2").map should be (Map("a" -> 1))
        }
      }
      "using short option" should {
        "parse empty" in {
          ClipTest("-m").map should be ('empty)
        }
        "parse space-separated" in {
          ClipTest("-ma=1 b=2").map should be (Map("a" -> 1, "b" -> 2))
        }
        "parse comma-separated" in {
          ClipTest("-ma=1,b=2").map should be (Map("a" -> 1, "b" -> 2))
        }
      }
    }
    "using tail" should {
      import scala.language.reflectiveCalls
      "parse a single trailing argument" in {
        val ct = new ClipTest("bla") { val end = tail[String] }
        ct.end should be ("bla")
      }
      "parse multiple trailing arguments" in {
        val ct = new ClipTest("bla 5 foo") {
          val first = tail[String]
          val second = tail[Int]
        }
        ct.first should be ("bla")
        ct.second should be (5)
        ct.trailing should be (Seq("foo"))
      }
      "parse trailing sequence arguments" in {
        val ct = new ClipTest("a b c") { val parsed = tail[Seq[String]] }
        ct.parsed should be (Seq("a", "b", "c"))
      }
      "fail on unparseable arguments" in {
        a[NumberFormatException] should be thrownBy {
          new ClipTest("a") { val end = tail[Int] }
        }
      }
      "fail for missing arguments" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("") { val end = tail[Int] }
        }
        an[IllegalStateException] should be thrownBy {
          new ClipTest("100") { val end = tail[Pixel] }
        }
      }
    }
    "using custom parsers" should {
      "parse" in {
        import java.net.InetAddress
        implicit val inetParser = new UnaryParser(InetAddress.getByName(_), "address")
        new ClipTest("-a 127.0.0.1") {
          val address = opt[InetAddress]("address")
        }.address should be (Some(InetAddress.getByName("127.0.0.1")))
      }
    }
    "used generally" should {
      "parse from multiple args" in {
        val ct = ClipTest("--byte 1 --int 2 --double 3.0")
        ct.byte should be (Some(1.toByte))
        ct.int should be (Some(2))
        ct.double should be (Some(3.0))
      }
      "error on unparseable args" in {
        a[NumberFormatException] should be thrownBy ClipTest("--int a")
      }
      "error on incorrect arg length" in {
        an[IllegalArgumentException] should be thrownBy ClipTest("--int")
      }
      "error on duplicate long names" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("") {
            val int2 = opt[Int]("int")
          }
        }
      }
      "error on duplicate short names" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("") { val flagFail = flag("xflag2") }
        }
      }
      "yield remaining arguments" in {
        ClipTest("start --byte 1 --int 2 --double 3.0 end").remaining should be (Seq("start", "end"))
      }
    }
    "used with NoDefaultShort behavior" should {
      "not generate short arguments" in {
        new ClipTest("-i 2", NoDefaultShort).int should be (None)
      }
    }
    "checking unrecognized options" should {
      "pass with no unrecognized options" in {
        new ClipTest("-i 2") { check(Unrecognized) }
      }
      "fail with unrecognized options" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("-i 2 -n") { check(Unrecognized) }
        }
      }
      "fail with unrecognized flags" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("-xyu") { check(Unrecognized) }
        }
      }
    }
    "checking repeated" should {
      "pass with no repeated options" in {
        new ClipTest("-i 2") { check(NoRepeated) }
      }
      "pass with short-only options" in {
        new ClipTest("-z") { check(NoRepeated) }
      }
      "fail for repeated long options" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("--int 1 --int 2") { check(NoRepeated) }
        }
      }
      "fail for repeated long + short options" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("--int 1 -i 2") { check(NoRepeated) }
        }
      }
      "fail for repeated grouped + short flags" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("-xyz -x") { check(NoRepeated) }
        }
      }
    }
    "checking no leading arguments" should {
      "pass with no extra arguments" in {
        new ClipTest("-i 2") { check(NoLeading) }
      }
      "pass with trailing arguments" in {
        new ClipTest("-i 2 foo") { check(NoLeading) }
      }
      "fail with leading arguments" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("foo -i 2") { check(NoLeading) }
        }
      }
    }
    "checking no extra arguments" should {
      "pass with no extra arguments" in {
        new ClipTest("-i 2") { check(NoExtra) }
      }
      "pass with trailing arguments" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("-i 2 foo") { check(NoExtra) }
        }
      }
      "fail with leading arguments" in {
        an[IllegalStateException] should be thrownBy {
          new ClipTest("foo -i 2") { check(NoExtra) }
        }
      }
    }
    "checking help" when {
      def helpClip(args: String) = new Clip(args.split("\\s")) {
        val isAsync = flag("async", desc = "If present, all requests are executed asynchronously")
        val host = ropt[String]("host", desc = "Host address")
        val port = dopt("port", 80, desc = "Host port, or 80 if missing")
        check(AutoHelp)
      }
      "generate help when called with --help" in {
        val help = the[HelpCalled] thrownBy helpClip("--help")
        help.getMessage.trim should be {
"""--help called:
Usage:
  [flags] --host string1 [options]

  --host|-h string1
      Host address

Flags:
  --async|-a
      If present, all requests are executed asynchronously

Options:
  --help
      Display this message
  --port|-p int1
      Host port, or 80 if missing"""
        }
      }
      "not generate help when called without --help" in {
        helpClip("-h 127.0.0.1")
      }
    }
  }
}