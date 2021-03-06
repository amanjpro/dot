package dot

import org.scalatest._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import scala.util.parsing.combinator.syntactical.StdTokenParsers
import scala.util.parsing.combinator.lexical.StdLexical
import scala.collection.immutable._
import util.parsing.combinator.{PackratParsers, ImplicitConversions}

trait LambdaParsing extends StdTokenParsers with BindingParsers with PackratParsers with LambdaNominalSyntax with ImplicitConversions { theParser =>
  type Tokens = StdLexical; val lexical = new StdLexical
  lexical.delimiters ++= List("\\",".", "(", ")", ":", "->", "*")

  type P[T] = PackratParser[T]

  def l[T](p: => Parser[T])(name: String): P[T] = Parser{ in =>
    val r = p(in)
    r
  }
  def BindingParser(envArg: Map[String, Name]): BindingParser = new BindingParser { val env = envArg }
  trait BindingParser extends BindingParserCore {
    lazy val term1: P[Term] = (
      l(bound(ident) ^^ {case x => Var(x)}) ("var") |
      l("\\" ~> bind(ident) >> {x => ":" ~> ty ~("." ~> under(x)(_.term))} ^^ {case ty~abs => Lam(ty, abs)}) ("lam") |
      l("(" ~> term <~ ")") ("paren")
    )
    lazy val term = chainl1(term1, l(success(App(_, _))) ("app"))

    lazy val ty: P[Type] = (
      l(ty ~ ("->" ~> ty) ^^ {case ty1~ty2 => Fun(ty1, ty2)}) ("arrow type")) |
      l(("*" | ident) ^^ {case base => T(base)}) ("base type")
  }
  def Parser = BindingParser(HashMap.empty)
}

@RunWith(classOf[JUnitRunner])
class TestBindingParsers extends FunSuite with LambdaParsing {
  def parse(in: String) = phrase(Parser.term)(new lexical.Scanner(in))

  val x = Name("x")
  val y = Name("y")
  val z = Name("z")
  val ty = T("*")

  def ok(expected: Term)(in: String) = parse(in) match {
    case Success(actual, _) => expectResult(expected)(actual)
    case _@r => fail("expected success, got " + r)
  }

  def bad(expectedMsg: String)(in: String) = parse(in) match {
    case Failure(msg, _) => expectResult(expectedMsg)(msg)
    case _@r => fail("expected failure, got " + r)
  }

  test("OK1") { ok(Lam(ty, x\\Var(x)))("\\x:*.x") }
  test("OK2") { ok(Lam(ty, x\\Lam(ty, y\\Var(x))))("\\x:*.\\y:*.x") }
  test("OK3") { ok(Lam(ty, x\\Lam(ty, y\\App(Var(x), Var(y)))))("\\x:*.\\y:*.(x y)") }
  test("OK4") { ok(Lam(ty, x\\Lam(ty, y\\App(App(Var(x), Var(y)), Var(x)))))("\\x:*.\\y:*.(x y) x") }
  test("OK3a") { ok(Lam(ty, x\\Lam(ty, y\\App(Var(x), Var(y)))))("\\x:*.\\y:*.x y") }
  test("OK4a") { ok(Lam(ty, x\\Lam(ty, y\\App(App(Var(x), Var(y)), Var(x)))))("\\x:*.\\y:*.x y x") }
  test("OK5") { ok(Lam(Fun(ty, Fun(ty, ty)), x\\Var(x)))("\\x:*->*->*.x") }

  test("Bad1") { bad("Unbound variable: x")("x") }
  test("Bad2") { bad("Unbound variable: x")("\\y:*.x") }
  test("Bad3") { bad("Unbound variable: x")("(\\y:*.x) (\\x:*.x)") }
}
