package fix

import scala.annotation.tailrec
import org.langmeta.semanticdb.Symbol.Global
import scala.meta._
import scalafix._, util._

final case class sbt_v1_1(index: SemanticdbIndex) extends SemanticRule(index, "sbt_v1_1") {

  // These are the scoping methods that we are rewriting to slash notation.
  // We define them inside Ex (stand for "extractor") to use them in pattern matching.

  val inScope = new Ex(
    "_root_.sbt.SettingKey#in(Lsbt/Scope;)Lsbt/SettingKey;.",
    "_root_.sbt.TaskKey#in(Lsbt/Scope;)Lsbt/TaskKey;.",
    "_root_.sbt.InputKey#in(Lsbt/Scope;)Lsbt/InputKey;."
  )

  val inRef    = new Ex("_root_.sbt.Scoped.ScopingSetting#in(Lsbt/Reference;)Ljava/lang/Object;.")
  val inScoped = new Ex("_root_.sbt.Scoped.ScopingSetting#in(Lsbt/Scoped;)Ljava/lang/Object;.")
  val inConf   = new Ex("_root_.sbt.Scoped.ScopingSetting#in(Lsbt/ConfigKey;)Ljava/lang/Object;.")

  val inConfAndScoped = new Ex(
    "_root_.sbt.Scoped.ScopingSetting#in(Lsbt/ConfigKey;Lsbt/Scoped;)Ljava/lang/Object;.")

  val inRefAndConf = new Ex(
    "_root_.sbt.Scoped.ScopingSetting#in(Lsbt/Reference;Lsbt/ConfigKey;)Ljava/lang/Object;.")

  val inRefAndScoped = new Ex(
    "_root_.sbt.Scoped.ScopingSetting#in(Lsbt/Reference;Lsbt/Scoped;)Ljava/lang/Object;.")

  val inRefAndConfAndScoped = new Ex(
    "_root_.sbt.Scoped.ScopingSetting#in(Lsbt/Reference;Lsbt/ConfigKey;Lsbt/Scoped;)Ljava/lang/Object;.")

  val in3ScopeAxes = new Ex(
    "_root_.sbt.Scoped.ScopingSetting#in(Lsbt/ScopeAxis;Lsbt/ScopeAxis;Lsbt/ScopeAxis;)Ljava/lang/Object;.")

  // TODO: Test this on plugin
  // TODO: Test this on build
  // TODO: Handle if SlashSyntax isn't in scope (import it)
  override def fix(ctx: RuleCtx): Patch = {
    implicit val implicitCtx: RuleCtx = ctx

    // Given a medium complexity expression `scalacOptions in Compile in Test`
    // The rewrite output should be `Test / scalacOptions`.

    // We'll use `ctx.tree.collect { .. }` which will hit the following terms in order:
    // 1. (scalacOptions in Compile) in Test
    // 2. scalacOptions in Compile
    // 3. scalacOptions

    // So in order to rewrite it correctly
    // We have to do some recursion of our own from the initial, root term.
    // In order to build up what the scope should be
    // Until we finally hit the key
    // So in this example we'll first hold onto the reference to `Test`
    // and recurse into the LHS term `scalacOptions in Compile`
    // We'll ignore `Compile`, given the config axis of Scope has already been defined to be Compile
    // and recurse again
    // and finally we'll hit `scalacOptions`.
    // at which point we'll construct `Test / scalacOptions`
    // to replace the initial, root term `scalacOptions in Compile in Test`

    // Then we need to subvert `ctx.tree.collect { .. }`
    // when it independently hits the inner `scalacOptions in Compile`
    // which will mess up the patching
    // To avoid this problem..
    // we'll use a mutable Set, and side-effecting to tell .collect to ignore these terms... :-/

    val handled = scala.collection.mutable.Set.empty[Term]

    /** Handle the term `term`.
     *
     * @param z the initial, root term we're handing
     * @param p the in-progress patch
     * @param term the term we're currently handling in this loop iteration
     * @param scope the scope we're building up
     * @return the patch for the initial, root term, possibly Patch.empty
     */
    // TODO: Collapse ScopeLike into a ScopeCtx, which includes z and p
    @tailrec def loop(z: Term, p: Patch, term: Term, scope: ScopeLike): Patch = {
      handled += term
      term match {
        case _ if scope == WeirdScope              => Patch.empty // opt: break recursion early
        case t inScope List(s)                     =>
          val (newScope, patch) = scope inScope s
          loop(z, p + patch, t, newScope)
        case t inRef List(r)                       => loop(z, p, t, scope inRef r)
        case t inScoped List(s)                    => loop(z, p, t, scope inScoped s)
        case t inConf List(c)                      => loop(z, p, t, scope inConf c)
        case t inConfAndScoped List(c, s)          => loop(z, p, t, scope inConf c inScoped s)
        case t inRefAndConf List(r, c)             => loop(z, p, t, scope inRef r inConf c)
        case t inRefAndScoped List(r, s)           => loop(z, p, t, scope inRef r inScoped s)
        case t inRefAndConfAndScoped List(r, c, s) => loop(z, p, t, scope inRef r inConf c inScoped s)
        case t in3ScopeAxes List(r, c, a)          => loop(z, p, t, scope inRef r inConf c inTask a)
        case _ if scope == Scope(None, None, None) => Patch.empty // opt: avoid replaceTree
        case _                                     =>
          def slash(lhs: Term, rhs: Term) = Term.ApplyInfix(lhs, Term.Name("/"), Nil, List(rhs))
          def replace(newTree: Term) = ctx.replaceTree(z, newTree.syntax) + preserveParens(z.tokens)
          scope match {
            case SpecificScope(scope) => replace(slash(scope, term))
            case s: Scope             => p + replace(List(s.ref, s.conf, s.task, Some(term)).flatten.reduce(slash))
            case WeirdScope           => Patch.empty
          }
      }
    }

    def preserveParens(tokens: Tokens) =
      for {
        head <- tokens.headOption
        if head.is[Token.LeftParen]
        last <- tokens.lastOption
        if last.is[Token.RightParen]
      } yield ctx.addLeft(head, "(") + ctx.addRight(last, ")")

    ctx.tree.collect {
      case term: Term if !handled(term) => loop(term, Patch.empty, term, Scope(None, None, None))
    }.asPatch
  }
}

/** An interim data type to define the target scope while traversing trees using the old syntax.
 * See the leaves for more details.
 */
sealed trait ScopeLike {
  def inRef(r: Term): ScopeLike  = modScope(s => s.copy(ref  = s.ref  orElse Some(r)))
  def inConf(c: Term): ScopeLike = modScope(s => s.copy(conf = s.conf orElse Some(c)))
  def inTask(a: Term): ScopeLike = modScope(s => s.copy(task = s.task orElse Some(a)))

  // TODO: See if it's possible to traverse the Semantic DB to understand if a key is already scoped.
  def inScoped(s: Term)(implicit index: SemanticdbIndex): ScopeLike = {
    val Keys = Symbol("_root_.sbt.Keys.")
    val isInKeys = index.symbol(s) match {
      case Some(Global(Keys, _)) => true
      case _                     => false
    }
    val attrKey = if (isInKeys) s else Term.Select(s, Term.Name("key"))
    inTask(attrKey)
  }

  /** This is the more complicated case when `in(Scope)` was reached.
   * If nothing is defined, e.g `cancelable in Global`, then `Global` is the specific scope.
   * Then we try and handle the 90% case - when Global/GlobalScope/ThisScope are the passed scopes.
   * Finally, for anything else we just bail, using `WeirdScope`.
   */
  def inScope(scope: Term)(implicit index: SemanticdbIndex, ctx: RuleCtx): (ScopeLike, Patch) = {
    val Global = SymbolMatcher.exact(
      Symbol("_root_.sbt.package.Global."),
      Symbol("_root_.sbt.package.GlobalScope.")
    )

    val ThisScope = SymbolMatcher.exact(Symbol("_root_.sbt.package.ThisScope."))

    this match {
      case x: SpecificScope              => (x, Patch.empty)
      case WeirdScope                    => (WeirdScope, Patch.empty)
      case s: Scope                      => s match {
        case Scope(None, None, None)       => (SpecificScope(scope), Patch.empty)
        case s if ThisScope.matches(scope) => (s, Patch.empty) // ThisScope is the noop
        case s if Global.matches(scope)    =>
          // Some parts of scope are already defined
          // and "in Global" was then reached
          // e.g `cancelable in Global in ThisBuild`
          // so replace anything not defined with `Zero`
          // (purposely ignoring that Global also means Zero "extra" axis.. yolo)
          val newScope = s.copy(
            ref  = s.ref  orElse Some(Term.Name("Zero")),
            conf = s.conf orElse Some(Term.Name("Zero")),
            task = s.task orElse Some(Term.Name("Zero"))
          )
          val Zero = Symbol("_root_.sbt.Zero.")
          val containsZero = ctx.index.names.exists(n => n.position.text == "Zero" && n.symbol == Zero)
          val patch = if (containsZero) Patch.empty else ctx.addGlobalImport(Zero)
          (newScope, patch)

        // e.g `cancelable in Global.copy(task = This) in ThisBuild`, we just bail..
        // TODO: Scope scope, then use slash syntax.
        case _ => (WeirdScope, Patch.empty)
      }
    }
  }

  private[this] def modScope(f: Scope => ScopeLike) = this match {
    case x: SpecificScope => x
    case s: Scope         => f(s)
    case WeirdScope       => WeirdScope
  }
}

/** This is used to build up the target scope, capturing each axis. */
final case class Scope(ref: Option[Term], conf: Option[Term], task: Option[Term]) extends ScopeLike

/** Captures when a very specific scope is specified, e.g `cancelable in Global`. */
final case class SpecificScope(scope: Term) extends ScopeLike

/** This is a fallback case for weird cases that can't be rewritten.
 * e.g. `cancelable in Global.copy(task = This) in ThisBuild`
 */
case object WeirdScope extends ScopeLike


final class Ex[A](symbols: String*)(implicit idx: SemanticdbIndex) {
  private[this] val in = SymbolMatcher.exact(symbols map (Symbol(_)): _*)

  def unapply(t: Tree): Option[(Term, List[Term])] = t match {
    case Term.ApplyInfix(lhs, in(_), Nil, rhs)    => Some((lhs, rhs))
    case Term.Apply(Term.Select(lhs, in(_)), rhs) => Some((lhs, rhs))
    case _                                        => None
  }
}
