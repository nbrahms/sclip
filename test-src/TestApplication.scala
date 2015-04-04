import org.nbrahms.sclip._

class Options(args: Array[String]) extends Clip(args) {
  // All options are declared as vals
  val port = dopt("port", 80)     // Optional, defaults to 80
  val host = ropt[String]("host") // Required
  check(AutoHelp)
}
object Application {
  def main(args: Array[String]) {
    val options = new Options(args)
    println(s"Connecting to ${options.host}:${options.port}")
  }
}