package dot

trait DotSyntax extends AbstractBindingSyntax { syntax =>
  import scala.util.parsing.input.Positional

  sealed trait Level {
    type Classifies <: Level
  }
  object Levels {
    trait TypeBounds extends Level { type Classifies = Type }
    trait Type extends Level { type Classifies = Term }
    trait ClassType extends Type
    trait AbstractType extends Type
    trait Term extends Level { type Classifies = Nothing }
    trait ArrowType extends Level { type Classifies = Method }
    trait Method extends Level { type Classifies = Nothing }
  }  

  sealed abstract class Label[+T <: Level](val name: String) extends Positional
  sealed abstract class TypeLabel(name: String, val isConcrete: Boolean) extends Label[Levels.Type](name)
  case class ClassLabel(override val name: String) extends TypeLabel(name, true)
  case class AbstractTypeLabel(override val name: String) extends TypeLabel(name, false)
  object TypeLabel {
    def unapply(tyl : TypeLabel): Option[(String, Boolean)] = Some((tyl.name, tyl.isConcrete))
  }
  case class ValueLabel(override val name: String) extends Label[Levels.Term](name)
  case class MethodLabel(override val name: String) extends Label[Levels.Method](name)

  sealed trait Entity extends Positional {
    type Level <: syntax.Level
  }

  trait Term extends Entity {
    type Level = Levels.Term
    def isPath = false
  }
  object Path {
    def unapply(t: Term): Option[Term] = if(t.isPath) Some(t) else None
  }

  case class Method(body: \\[Term]) extends Entity {
    type Level = Levels.Method
  }

  trait Type extends Entity {
    type Level = Levels.Type
    def isConcrete = false
  }
  object ClassType {
    def unapply(tp: Type): Option[Type] = if(tp.isConcrete) Some(tp) else None
  }

  sealed abstract class TypePair[Level](val lo: Type, val hi: Type) extends Entity

  case class TypeBounds(override val lo: Type, override val hi: Type) extends TypePair[Levels.TypeBounds](lo, hi)
  case class ArrowType(val paramType: Type, val bodyType: Type) extends TypePair[Levels.ArrowType](paramType, bodyType)

  object Members {
    type Dcl = Decl[Level, Entity]
    sealed abstract class DeclOrDef[+L <: Level](val l: Label[L]) extends Positional
    sealed abstract class Decl[+L <: Level, +E <: Entity](override val l: Label[L], val cls: E) extends DeclOrDef[L](l)
    case class TypeDecl(override val l: TypeLabel, override val cls: TypeBounds) extends Decl(l, cls)
    case class ValueDecl(override val l: ValueLabel, override val cls: Type) extends Decl(l, cls)
    case class MethodDecl(override val l: MethodLabel, override val cls: ArrowType) extends Decl(l, cls)
    abstract class Dcls extends Positional {
      def findByLabel[L <: Level](l: Label[L]): Option[Dcl]
    }
    case class Decls(decls: List[Dcl]) extends Dcls {
      assert(uniqLabels(decls))
      override def findByLabel[L <: Level](l: Label[L]) = decls.find(d => d.l==l)
    }
    case object BottomDecls extends Dcls {
      val s = Types.Top; val u = Types.Bottom
      override def findByLabel[L <: Level](l: Label[L]) = Some(l match {
        case (l: TypeLabel) => TypeDecl(l, TypeBounds(s, u))
        case (l: MethodLabel) => MethodDecl(l, ArrowType(s, u))
        case (l: ValueLabel) => ValueDecl(l, u)
      })
    }

    type Defn = Def[Level, Entity]
    sealed abstract class Def[+L <: Level, +E <: Entity](override val l: Label[L], val rhs: E) extends DeclOrDef[L](l)
    case class ValueDef(override val l: ValueLabel, override val rhs: Terms.Value) extends Def(l, rhs)
    case class MethodDef(override val l: MethodLabel, override val rhs: Method) extends Def(l, rhs)
    case class Defs(defs: List[Defn]) extends Positional {
      assert(uniqLabels(defs))
    }

    def uniqLabels[D <: DeclOrDef[_]](ds: List[D]): Boolean = ds forall{ d1 =>
      val n = ds.filter(d2 => d1.l==d2.l).size
      n==1
    }
  }

  object Terms {
    trait Value extends Term
    case class Var(name: Name) extends Value {
      override def isPath = true
    }
    case class Sel(o: Term, label: ValueLabel) extends Term {
      override def isPath = o.isPath
    }
    case class Msel(o: Term, label: MethodLabel, a: Term) extends Term
    case class New(tpe: Type, args_scope: \\[(Members.Defs, Term)]) extends Term {
      assert(tpe.isConcrete)
    }
  }

  object Types {
    case class Tsel(o: Term, label: TypeLabel) extends Type {
      assert(o.isPath)
      override def isConcrete = label.isConcrete
    }
    case class Refine(parent: Type, decls: \\[Members.Decls]) extends Type {
      override def isConcrete = parent.isConcrete
    }
    case class Intersect(a: Type, b: Type) extends Type {
      override def isConcrete = a.isConcrete && b.isConcrete
    }
    case class Union(a: Type, b: Type) extends Type
    case object Top extends Type {
      override def isConcrete = true
    }
    case object Bottom extends Type
  }
}

trait DotNominalSyntax extends DotSyntax with NominalBindingSyntax { self: DotSyntax =>
  implicit def typeLabelHasBinders: ContainsBinders[TypeLabel] = AtomicIsNominal[TypeLabel](_)
  implicit def valueLabelHasBinders: ContainsBinders[ValueLabel] = AtomicIsNominal[ValueLabel](_)
  implicit def methodLabelHasBinders: ContainsBinders[MethodLabel] = AtomicIsNominal[MethodLabel](_)

  implicit def termHasBinders: ContainsBinders[Term] = (tm: Term) => new Nominal[Term] {
    import Terms._
    def swap(a: Name, b: Name): Term = tm match {
      case Var(n) => Var(n swap(a, b)).setPos(tm.pos)
      case Sel(obj, l) => Sel(obj swap(a, b), l swap(a, b)).setPos(tm.pos)
      case Msel(obj, m, arg) => Msel(obj swap(a, b), m swap(a, b), arg swap(a, b)).setPos(tm.pos)
      case New(tpe, args_scope) => New(tpe swap(a, b), args_scope swap(a, b)).setPos(tm.pos)
    }
    def fresh(a: Name): Boolean = tm match {
      case Var(n) => n fresh(a)
      case Sel(obj, l) => obj.fresh(a) && l.fresh(a)
      case Msel(obj, m, arg) => obj.fresh(a) && m.fresh(a) && arg.fresh(a)
      case New(tpe, args_scope) => tpe.fresh(a) && args_scope.fresh(a)
    }
  }

  implicit val typeHasBinders: ContainsBinders[Type] = (tp: Type) => new Nominal[Type] {
    import Types._
    def swap(a: Name, b: Name): Type = tp match {
      case Tsel(obj, l) => Tsel(obj swap(a, b), l swap(a, b)).setPos(tp.pos)
      case Refine(parent, decls) => Refine(parent swap(a, b), decls swap(a, b)).setPos(tp.pos)
      case Intersect(tp1, tp2) => Intersect(tp1 swap(a, b), tp2 swap(a, b)).setPos(tp.pos)
      case Union(tp1, tp2) => Union(tp1 swap(a, b), tp2 swap(a, b)).setPos(tp.pos)
      case Top => Top.setPos(tp.pos)
      case Bottom => Bottom.setPos(tp.pos)
    }
    def fresh(a: Name): Boolean = tp match {
      case Tsel(obj, l) => obj.fresh(a) && l.fresh(a)
      case Refine(parent, decls) => parent.fresh(a) && decls.fresh(a)
      case Intersect(tp1, tp2) => tp1.fresh(a) && tp2.fresh(a)
      case Union(tp1, tp2) => tp1.fresh(a) && tp2.fresh(a)
      case Top => true
      case Bottom => true
    }
  }

  implicit val typeBoundsHasBinders: ContainsBinders[TypeBounds] = (k: TypeBounds) => new Nominal[TypeBounds] {
    def swap(a: Name, b: Name): TypeBounds = k match {
      case TypeBounds(lo, hi) => TypeBounds(lo swap(a, b), hi swap(a, b)).setPos(k.pos)
    }
    def fresh(a: Name): Boolean = k match {
      case TypeBounds(lo, hi) => lo.fresh(a) && hi.fresh(a)
    }
  }

  implicit val arrowTypeHasBinders: ContainsBinders[ArrowType] = (k: ArrowType) => new Nominal[ArrowType] {
    def swap(a: Name, b: Name): ArrowType = k match {
      case ArrowType(lo, hi) => ArrowType(lo swap(a, b), hi swap(a, b)).setPos(k.pos)
    }
    def fresh(a: Name): Boolean = k match {
      case ArrowType(lo, hi) => lo.fresh(a) && hi.fresh(a)
    }
  }

  implicit def memDeclHasBinders: ContainsBinders[Members.Dcl] = (mem: Members.Dcl) => new Nominal[Members.Dcl] {
    import Members._
    def swap(a: Name, b: Name): Members.Dcl = mem match {
      case TypeDecl(l, cls) => TypeDecl(l swap(a, b), cls swap(a, b)).setPos(mem.pos)
      case ValueDecl(l, cls) => ValueDecl(l swap(a, b), cls swap(a, b)).setPos(mem.pos)
      case MethodDecl(l, cls) => MethodDecl(l swap(a, b), cls swap(a, b)).setPos(mem.pos)
    }
    def fresh(a: Name): Boolean = mem match {
      case TypeDecl(l, cls) => l.fresh(a) && cls.fresh(a)
      case ValueDecl(l, cls) => l.fresh(a) && cls.fresh(a)
      case MethodDecl(l, cls) => l.fresh(a) && cls.fresh(a)
    }
  }

  implicit def valueHasBinders: ContainsBinders[Terms.Value] = (tm: Terms.Value) => new Nominal[Terms.Value] {
    import Terms._
    def swap(a: Name, b: Name): Value = tm match {
      case Var(n) => Var(n swap(a, b)).setPos(tm.pos)
    }
    def fresh(a: Name): Boolean = tm match {
      case Var(n) => n fresh(a)
    }
  }

  implicit def methodHasBinders: ContainsBinders[Method] = (method: Method) => new Nominal[Method] {
    def swap(a: Name, b: Name): Method = method match {
      case Method(body) => Method(body swap(a, b)).setPos(method.pos)
    }
    def fresh(a: Name): Boolean = method match {
      case Method(body) => body fresh(a)
    }
  }

  implicit def memDefHasBinders: ContainsBinders[Members.Defn] = (mem: Members.Defn) => new Nominal[Members.Defn] {
    import Members._
    def swap(a: Name, b: Name): Members.Defn = mem match {
      case ValueDef(l, rhs) => ValueDef(l swap(a, b), valueHasBinders(rhs) swap(a, b)).setPos(mem.pos)
      case MethodDef(l, rhs) => MethodDef(l swap(a, b), rhs swap(a, b)).setPos(mem.pos)
    }
    def fresh(a: Name): Boolean = mem match {
      case ValueDef(l, rhs) => l.fresh(a) && valueHasBinders(rhs).fresh(a)
      case MethodDef(l, rhs) => l.fresh(a) && rhs.fresh(a)
    }
  }

  implicit def declsHasBinders: ContainsBinders[Members.Decls] = (ds: Members.Decls) => new Nominal[Members.Decls] {
    import Members._
    def swap(a: Name, b: Name): Decls = ds match {
      case Decls(ds) => Decls(ds swap(a, b))
    }
    def fresh(a: Name): Boolean = ds match {
      case Decls(ds) => ds.fresh(a)
    }
  }

  implicit def defsHasBinders: ContainsBinders[Members.Defs] = (ds: Members.Defs) => new Nominal[Members.Defs] {
    import Members._
    def swap(a: Name, b: Name): Defs = ds match {
      case Defs(ds) => Defs(ds swap(a, b))
    }
    def fresh(a: Name): Boolean = ds match {
      case Defs(ds) => ds.fresh(a)
    }
  }

  val entityHasBinders: ContainsBinders[Entity] = (e: Entity) => new Nominal[Entity] {
    def swap(a: Name, b: Name): Entity = e match {
      case (e: TypeBounds) => typeBoundsHasBinders(e) swap(a, b)
      case (e: Type) => typeHasBinders(e) swap(a, b)
      case (e: Term) => termHasBinders(e) swap(a, b)
      case (e: ArrowType) => arrowTypeHasBinders(e) swap(a, b)
      case (e: Method) => methodHasBinders(e) swap(a, b)
    }
    def fresh(a: Name): Boolean = e match {
      case (e: TypeBounds) => typeBoundsHasBinders(e) fresh(a)
      case (e: Type) => typeHasBinders(e) fresh(a)
      case (e: Term) => termHasBinders(e) fresh(a)
      case (e: ArrowType) => arrowTypeHasBinders(e) fresh(a)
      case (e: Method) => methodHasBinders(e) fresh(a)
    }
  }
}

trait DotSubstitution extends DotNominalSyntax {
  import Terms._
  import Types._
  import Members._

  implicit def scopedIsTermSubstable[T](in: \\[T])(implicit wSubs: T => Substable[Term, T], wNom: T => Nominal[T]): Substable[Term, \\[T]] = scopedIsSubstable[T, Term, T](in)
  implicit def listIsTermSubstable[T: Substable[Term, T]#From](in: List[T]): Substable[Term, List[T]] = listIsSubstable[T, Term, T](in)

  implicit def termIsSubstable(in: Term): Substable[Term, Term] = new Substable[Term, Term] {
    def subst(from: Name, to: Term): Term = in match {
      case Var(`from`) => to
      case Var(_) => in
      case Sel(obj, l) => Sel(obj subst(from, to), l).setPos(in.pos)
      case Msel(obj, m, arg) => Msel(obj subst(from, to), m, arg subst(from, to)).setPos(in.pos)
      case New(tpe, args_scope) => New(tpe subst(from, to), args_scope subst(from, to)).setPos(in.pos)
    }
  }

  implicit def typeIsSubstable(in: Type): Substable[Term, Type] = new Substable[Term, Type] {
    def subst(from: Name, to: Term): Type = in match {
      case Tsel(obj, l) => Tsel(obj subst(from, to), l).setPos(in.pos)
      case Refine(parent, decls) => Refine(parent subst(from, to), decls subst(from, to)).setPos(in.pos)
      case Intersect(a, b) => Intersect(a subst(from, to), b subst(from, to)).setPos(in.pos)
      case Union(a, b) => Union(a subst(from, to), b subst(from, to)).setPos(in.pos)
      case Top => Top.setPos(in.pos)
      case Bottom => Bottom.setPos(in.pos)
    }
  }

  implicit def typeBoundsIsSubstable(in: TypeBounds): Substable[Term, TypeBounds] = new Substable[Term, TypeBounds] {
    def subst(from: Name, to: Term): TypeBounds = in match {
      case TypeBounds(lo, hi) => TypeBounds(lo subst(from, to), hi subst(from, to)).setPos(in.pos)
    }
  }

  implicit def arrowTypeIsSubstable(in: ArrowType): Substable[Term, ArrowType] = new Substable[Term, ArrowType] {
    def subst(from: Name, to: Term): ArrowType = in match {
      case ArrowType(lo, hi) => ArrowType(lo subst(from, to), hi subst(from, to)).setPos(in.pos)
    }
  }

  implicit def memDeclIsSubstable(in: Dcl): Substable[Term, Dcl] = new Substable[Term, Dcl] {
    def subst(from: Name, to: Term): Dcl = in match {
      case TypeDecl(l, cls) => TypeDecl(l, cls subst(from, to)).setPos(in.pos)
      case ValueDecl(l, cls) => ValueDecl(l, cls subst(from, to)).setPos(in.pos)
      case MethodDecl(l, cls) => MethodDecl(l, cls subst(from, to)).setPos(in.pos)
    }
  }

  implicit def valueIsSubstable(in: Value): Substable[Term, Value] = new Substable[Term, Value] {
    def subst(from: Name, to: Term): Value =
      termIsSubstable(in).subst(from, to).asInstanceOf[Value]
  }

  implicit def methodIsSubstable(in: Method): Substable[Term, Method] = new Substable[Term, Method] {
    def subst(from: Name, to: Term): Method = in match {
      case Method(body) => Method(body subst(from, to)).setPos(in.pos)
    }
  }

  implicit def memDefIsSubstable(in: Defn): Substable[Term, Defn] = new Substable[Term, Defn] {
    def subst(from: Name, to: Term): Defn = in match {
      case ValueDef(l, rhs) => ValueDef(l, rhs subst(from, to)).setPos(in.pos)
      case MethodDef(l, rhs) => MethodDef(l, rhs subst(from, to)).setPos(in.pos)
    }
  }

  implicit def declsIsSubstable(in: Decls): Substable[Term, Decls] = new Substable[Term, Decls] {
    def subst(from: Name, to: Term): Decls = in match {
      case Decls(ds) => Decls(ds subst(from, to)).setPos(in.pos)
    }
  }

  implicit def defsIsSubstable(in: Defs): Substable[Term, Defs] = new Substable[Term, Defs] {
    def subst(from: Name, to: Term): Defs = in match {
      case Defs(ds) => Defs(ds subst(from, to)).setPos(in.pos)
    }
  }


  def entityIsSubstable(in: Entity): Substable[Term, Entity] = new Substable[Term, Entity] {
    def subst(from: Name, to: Term): Entity = in match {
      case (e: TypeBounds) => typeBoundsIsSubstable(e) subst(from, to)
      case (e: Type) => typeIsSubstable(e) subst(from, to)
      case (e: Term) => termIsSubstable(e) subst(from, to)
      case (e: ArrowType) => arrowTypeIsSubstable(e) subst(from, to)
      case (e: Method) => methodIsSubstable(e) subst(from, to)
    }
  }
}
