package org.nbrahms

/**
 * Scala Command-Line Interface Parser
 *
 * Provides an API for parsing and using a command-line interface.
 *
 * Example:
 * {{{
 *   import java.net.InetAddress
 *   import org.nbrahms.sclip._
 *
 *   class Options(args: Array[String]) extends Clip(args) {
 *     implicit val ipParser = new UnaryParser(InetAddress.getByName(_))
 *
 *     val port = dopt("port", 80)
 *     val host = ropt[InetAddress]("host", n => s"Please specify a host using --$n")
 *
 *     check(Unrecognized, Strict)
 *   }
 *
 *   class Application {
 *     def run(host: InetAddress, port: Int) {
 *       ...
 *     }
 *     def main(args: Array[String]) {
 *       val options = new Options(args)
 *       run(options.host, options.port)
 *     }
 *   }
 * }}}
 *
 * The above code will accept command lines of the form:
 * {{{
 *   java Application [(--port|-p) <port>] (--host|-h) <address>
 * }}}
 *
 * For more usage examples, see SclipSpec.scala.
 */
package object sclip {
  /**A parser object that converts a sequence of (white-space-free)
   * strings to an object of type A.
   *
   * @param arity The number of parsed arguments, or -1 if the parser will
   *              consume all remaining strings
   */
  abstract class Parser[A](val arity: Int) {
    /**Converts a string sequence to a tuple of (object, remaining strings).*/
    def apply(input: Seq[String]): (A, Seq[String])
    def argNames: Seq[String]
  }

  object NullaryParser extends Parser[Unit](0) {
    def apply(input: Seq[String]) = (Unit, input)
    def argNames = Seq.empty
  }

  /**Converts the sequence head to an object of type A.*/
  class UnaryParser[A](f: String => A, name: String) extends Parser[A](1) {
    def apply(input: Seq[String]) = (f(input.head), input.tail)
    def argNames = Seq(name)
  }

  /**A command-line argument validation to apply.
   *
   * To use, call [[Clip.check]] after setup.
   */
  sealed trait Check

  /**Validates that no unrecognized options were passed.
   *
   * Note that extra appearances of a defined argument will be treated as unrecognized arguments.
   * For example, with an "apple" flag defined, the second argument will be unrecognized:
   * ```-a --apple```.
   */
  object Unrecognized extends Check

  /**Validates that no arguments were unparsed in a position before the last
   * parsed argument.*/
  object NoLeading extends Check

  /**Validates that no unparsed arguments remain.
   *
   * This check subsumes [[NoLeading]].
   */
  object NoExtra extends Check

  /**Validates that no options are repeated.*/
  object NoRepeated extends Check

  /**Prints help if the user enters --help. Obviously this should not be used in conjunction with
   * your own "help" option.
   */
  object AutoHelp extends Check

  /**Modifies the behavior of argument parsing.*/
  sealed trait Behavior

  /**When present, options have only a long form, unless
   * defined as a single-character option or hasShort is set to true.
   *
   * By default, options have both a long and short form, e.g.:
   * `--host` or `-h`.
   */
  object NoDefaultShort extends Behavior

  class HelpCalled(msg: String) extends Exception(msg) with scala.util.control.NoStackTrace

  trait LowPriorityImplicits {
    import shapeless._

    implicit val stringParser = new UnaryParser[String](x => x, "string")
    implicit val byteParser = new UnaryParser(augmentString(_).toByte, "byte")
    implicit val intParser = new UnaryParser(augmentString(_).toInt, "int")
    implicit val longParser = new UnaryParser(augmentString(_).toLong, "long")
    implicit val floatParser = new UnaryParser(augmentString(_).toFloat, "float")
    implicit val doubleParser = new UnaryParser(augmentString(_).toDouble, "double")
    implicit val booleanParser = new UnaryParser(augmentString(_).toBoolean, "boolean")
    implicit val fileParser = new UnaryParser(new java.io.File(_), "path")

    implicit def seqParser[A](implicit elParser: Parser[A]) = new Parser[Seq[A]](-1) {
      def apply(input: Seq[String]) = {
        if (elParser.arity <= 0) throw new IllegalArgumentException("Elements must have finite arity")
        var rest = input
        val builder = new scala.collection.immutable.VectorBuilder[A]
        while (rest.size >= elParser.arity) {
          val (el, r) = elParser(rest)
          builder += el
          rest = r
        }
        (builder.result(), rest)
      }
      def argNames = elParser.argNames
    }
    implicit def tupleParser[T, H <: HList](implicit gen: Generic.Aux[T, H], hlistParser: Parser[H]) =
      new Parser[T](hlistParser.arity) {
        def apply(s: Seq[String]) = {
          val (h, rest) = hlistParser(s)
          (gen.from(h), rest)
        }
        def argNames = hlistParser.argNames
      }
    implicit def hnilParser = new Parser[HNil](0) {
        def apply(s: Seq[String]) = (HNil, s)
        def argNames = Seq.empty
      }
    implicit def hlistParser[A, H <: HList](implicit headParser: Parser[A], tailParser: Parser[H]) =
      new Parser[A :: H](headParser.arity + tailParser.arity) {
        def apply(s: Seq[String]) = {
          val (a, t) = headParser(s)
          val (h, rest) = tailParser(t)
          (a :: h, rest)
        }
        def argNames = headParser.argNames ++ tailParser.argNames
      }
  }

  import scala.language.existentials
  protected[sclip] case class Registration(longName: String, shortName: Option[Char], parser: Parser[_], isRequired: Boolean, isKv: Boolean, desc: String)
  implicit protected[sclip] class Registry(val map: Map[String, Registration]) extends AnyVal {
    def hasShort(short: Char) = map.find(_._2.shortName.map(_ == short).getOrElse(false)).isDefined
    def allFlags = map.filter(_._2.parser.arity == 0)
    def required = map.filter(_._2.isRequired)
    def optional = map.filter(kv => kv._2.parser.arity != 0 && !kv._2.isRequired)
  }

  /**Base class for configurations.
   *
   * To use, instantiate a new singleton instance with the command-line
   * arguments as constructor argument. Then assign options using one or more
   * of this class's protected methods.
   *
   * To view in ScalaDoc, hide members from [[LowPriorityImplicits]] and enable "All"
   * in visibility.
   */
  abstract class Clip(args: Seq[String], behaviors: Behavior*) extends LowPriorityImplicits {
    val defaultShortForm = !(behaviors contains NoDefaultShort)

    private[this] var _filtered = args.filterNot(_.isEmpty)
    private[this] var _registry = Map.empty[String, Registration]
    private[this] var _remaining = _filtered
    private[this] val _flagGroup: Set[Char] =
      _remaining.indexWhere(_.matches("-[a-zA-Z0-9]{2,}")) match {
        case -1 => Set.empty
        case ix =>
          val (front, rest) = _remaining.splitAt(ix)
          _remaining = front ++ rest.tail
          rest.head.toSet.drop(1)
      }
    private[this] var _remainingFlags = _flagGroup
    private def alias(name: String, hasShort: Boolean, shortChar: Char): Option[Char] =
      if (!hasShort) None
      else Some(if (shortChar == 0) name.head else shortChar)
    private def rip(name: String, num: Int, strict: Boolean): Option[Seq[String]] = {
      val ix = _remaining.indexWhere(s => if (strict) s == name else s startsWith name)
      if (ix == -1) return None
      val (front, rest) = _remaining.splitAt(ix)
      val headArg = rest.head.drop(name.size)
      val toParse = (if (!headArg.isEmpty) headArg +: rest.tail else rest.tail)
      val splitIx = if (num >= 0) num else {
        val nix = toParse.indexWhere(_ startsWith "-{1,2}\\d+\\M")
        if (nix >= 0) nix else toParse.size
      }
      val (result, back) = toParse.splitAt(splitIx)
      _remaining = front ++ back
      Some(result)
    }
    private def ripWithShort(name: String, num: Int, shortChar: Option[Char], strict: Boolean = true): Option[Seq[String]] = {
      rip("--" + name, num, true) orElse (shortChar flatMap (sc => rip("-" + sc, num, strict)))
    }
    private def register(name: String, shortChar: Option[Char], parser: Parser[_], isRequired: Boolean, isKv: Boolean = false, desc: String = "") {
      if (_registry.contains(name)) throw new IllegalStateException(s"$name is already registered as an option")
      if (name.size < 1) throw new IllegalArgumentException("Can not have empty option names")
      if (name.size == 1 && shortChar.isEmpty) throw new IllegalStateException("Single-character options must use short form")
      val key = if (name.size > 1) "--" + name else "-" + shortChar.get
      val short = shortChar foreach { sc =>
        if (_registry.hasShort(sc)) throw new IllegalStateException(s"Alias $sc is already used")
      }
      _registry += key -> new Registration(name, shortChar, parser, isRequired, isKv, desc)
    }
    private def help() {
      def toValues(reg: Registration, addDesc: Boolean = true) = {
        val firstLine =
          if (reg.parser.arity >= 0) reg.parser.argNames.zipWithIndex map (nix => s" ${nix._1}${nix._2 + 1}") mkString ""
          else if (!reg.isKv) s" ${reg.parser.argNames.map(_ + "1")} ..."
          else s" key1=${reg.parser.argNames.head}1 ..."   // kv parsers are always unary
        val secondLine = if (!addDesc || reg.desc == "") "" else "\n      " +
          reg.desc.trim.replaceAll("\\n", "\n      ")
        firstLine + secondLine
      }
      def toDesc(heading: String, map: Iterable[(String, Registration)]) =
        if (map.isEmpty) ""
        else
          s"$heading" + map.map { case (long, reg) =>
            s"  $long${reg.shortName.map("|-" + _).getOrElse("")}" + toValues(reg)
          }.mkString("\n")
      val flags = _registry.allFlags.toSeq.sortBy(_._1)
      val flagHeadLine = if (!flags.isEmpty) "[flags]" else ""
      val flagDesc = toDesc("\n\nFlags:\n", flags)

      val required = _registry.required.toSeq.sortBy(_._1)
      val requiredHeadLine = required map { kv => " " + kv._1 + toValues(kv._2, false) } mkString ""
      val requiredDesc = toDesc("\n\n", required)

      val options = _registry.optional.toSeq.sortBy(_._1)
      val optionHeadLine = if (!options.isEmpty) " [options]" else ""
      val optionDesc = toDesc("\n\nOptions:\n", options)

      val msg = s"--help called:\nUsage:\n  $flagHeadLine$requiredHeadLine$optionHeadLine$requiredDesc$flagDesc$optionDesc"
      throw new HelpCalled(msg)
    }

    /**Create a new single-value option.
     *
     * If {@code hasShort} is true, parses
     * {{{
     *   (--opt|-o) value
     * }}}
     * otherwise parses
     * {{{
     *   --opt value
     * }}}
     *
     * To change the short option alias, set {@code shortChar}.
     *
     * If the option is present, returns a Some of the parsed value, otherwise None.
     */
    protected def opt[A](name: String, hasShort: Boolean = defaultShortForm, shortChar: Char = 0,
        isRequired: Boolean = false, desc: String = "")(implicit parser: Parser[A]): Option[A] = {
      val sc = alias(name, hasShort, shortChar)
      register(name, sc, parser, isRequired, desc = desc)
      try {
        ripWithShort(name, parser.arity, sc).map(s => parser(s)._1)
      } catch {
        case ex: NoSuchElementException => throw new IllegalArgumentException(s"Missing argument after option '$name'")
      }
    }

    /**Create a new single-valued option with default value
     *
     * As for opt, but returns the parsed value directly if the option is present, otherwise the default value.
     */
    protected def dopt[A](name: String, default: => A, hasShort: Boolean = defaultShortForm, shortChar: Char = 0,
        isRequired: Boolean = false, desc: String = "")(implicit parser: Parser[A]): A =
      opt(name, hasShort, shortChar, desc = desc)(parser).getOrElse(default)

    /**Create a new single-valued required option
     *
     * As for opt, but throws an IllegalArgumentException if the option is missing.
     */
    protected def ropt[A](name: String, message: String => String = s => s"Missing required argument $s",
        hasShort: Boolean = defaultShortForm, shortChar: Char = 0, desc: String = "")(implicit parser: Parser[A]): A =
      opt(name, hasShort, shortChar, true, desc = desc)(parser) getOrElse {
        if (!(remaining contains "--help")) throw new IllegalArgumentException(message(name))
        else null.asInstanceOf[A]
      }

    /**Create a new sequence value.
     *
     * Parses all components until the next option.
     * 
     * If {@code hasShort} is true, parses
     * {{{
     *   (--opt|-o) value1 value2 value3 ...
     * }}}
     * otherwise parses
     * {{{
     *   --opt value1 value2 value3 ...
     * }}}
     */
    protected def sopt[A](name: String, hasShort: Boolean = defaultShortForm, shortChar: Char = 0,
        desc: String = "")(implicit parser: Parser[A]): Seq[A] =
      opt[Seq[A]](name, hasShort, shortChar, false, desc = desc).getOrElse(Seq.empty)

    /**Create a new flag.
     *
     * If {@code hasShort} is true, parses
     * {{{
     *   (--opt|-o)
     * }}}
     * otherwise parses
     * {{{
     *   --opt
     * }}}
     *
     * Also parses grouped flags, as in
     * {{{
     *   ls -lhtr
     * }}}
     *
     * Returns true iff the flag is present.
     */
    protected def flag(name: String, hasShort: Boolean = defaultShortForm, shortChar: Char = 0, desc: String = ""): Boolean = {
      val sc = alias(name, hasShort, shortChar)
      register(name, sc, NullaryParser, false, desc = desc)
      sc.filter(_remainingFlags contains _).map { c =>
        _remainingFlags -= c
        true
      } getOrElse ripWithShort(name, 0, sc).isDefined
    }

    /**Create a new key-value option.
     *
     * Parses
     * {{{
     *   --opt k1=v1,k2=v2,...
     *   --opt k1=v1 k2=v2 ...
     * }}}
     *
     * If {@code hasShort} is true, also parses
     * {{{
     *   -o k1=v1,k2=v2,...
     *   -o k1=v1 k2=v2 ...
     *   -ok1=v1,k2=v2,...
     * }}}
     */
    protected def kv[A](name: String, hasShort: Boolean = defaultShortForm, shortChar: Char = 0,
        desc: String = "")(implicit parser: UnaryParser[A]): Map[String, A] = {
      val sc = alias(name, hasShort, shortChar)
      register(name, sc, seqParser[A], false, isKv = true, desc = desc)
      val toParse = ripWithShort(name, -1, sc, false).toSeq.flatten
      val pairs = toParse.flatMap(_.split(",")).filterNot(_.isEmpty)
      (Map.empty[String, A] /: pairs) { (m, p) =>
        val (Array(k, v)) = p.split("=")
        m + (k -> parser(Seq(v))._1)
      }
    }

    /**Parse trailing arguments.
     */
    protected def tail[A](implicit parser: Parser[A]): A = {
      val trails = trailing
      if (trails.size < parser.arity)
        throw new IllegalStateException(s"There are not enough arguments left in ${trails.toList} to parse trailing arguments with arity ${parser.arity}")
      val (toParse, newTrailing) =
        if (parser.arity >= 0) trails splitAt parser.arity else (trails, Nil)
      _remaining = _remaining.filterNot(trails contains _) ++ newTrailing
      parser(toParse)._1
    }

    /**Returns all unparsed arguments.*/
    def remaining = _remaining

    /**Returns all unparsed arguments after the last parsed option.*/
    def trailing: Seq[String] = {
      import scala.annotation.tailrec
      if (remaining.size == 0) remaining
      else {
        @tailrec def strip(args: List[String], remaining: List[String], out: List[String]): Seq[String] =
          (args, remaining, out) match {
            case (_, Nil, o) => o
            case (a :: atail, r :: rtail, o) if a != r => o
            case (a :: atail, r :: rtail, o) => strip(atail, rtail, a :: o)
          }
        strip(_filtered.reverse.toList, remaining.reverse.toList, Nil)
      }
    }

    /**Apply one or more [[Check]]s.
     *
     * This method should only be called '''after''' object extraction.
     */
    protected def check(checks: Check*) {
      checks foreach {
        case NoRepeated =>
          _registry foreach { case (long, reg) =>
            val nLong = _filtered.filter(a => a == long && a.startsWith("--")).size
            val nShort = reg.shortName map { short =>
              (if (_flagGroup contains short) 1 else 0) +
              _filtered.filter(_ == "-" + short).size
            } getOrElse 0
            if (nLong + nShort > 1)
              throw new IllegalStateException(s"Repeated option $long")
          }
        case Unrecognized =>
          remaining find (_ startsWith "-") foreach { arg =>
            throw new IllegalStateException(s"Unrecognized or repeated option $arg")
          }
          if (!_flagGroup.isEmpty)
            throw new IllegalStateException(s"Unrecognized flags ${_flagGroup.mkString(",")}")
        case NoLeading | NoExtra if remaining.isEmpty =>
        case NoLeading =>
          val ix = _filtered indexWhere (_ == remaining.head)
          if ((_filtered.size - ix) != remaining.size)
            throw new IllegalStateException(s"Invalid input ${remaining.head}")
        case NoExtra =>
          if (remaining.size != 0)
            throw new IllegalStateException(s"Invalid input ${remaining.head}")
        case AutoHelp if remaining contains "--help" =>
          help()
        case _ =>
      }
    }
  }
}
