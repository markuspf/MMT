package info.kwarc.mmt.api.uom

import info.kwarc.mmt.api._
import objects._
import modules._
import symbols._

/**
 * a model of an MMT theory in Scala
 */
abstract class RealizationInScala extends DeclaredTheory(null, null, None) with RuleCreators {
   // getClass only works inside the body, i.e., after initializing the super class
   // so we make the constructor arguments null and override afterwards
   // this will fail if one of the arguments is accessed during initialization of the superclass
   override val parent = GenericScalaExporter.scalaToDPath(getClass.getPackage.getName)
   override val name = {
      val cls = getClass
      var n = cls.getName.substring(cls.getPackage.getName.length+1)
      if (n.endsWith("$"))
         n = n.substring(0,n.length-1)
      LocalName(n)
   }
   
   /** the modelled theory */
   val _domain: TheoryScala
   
   /** the MMT URI of the modelled theory */
   lazy val _path = _domain._path
   /** the name of the modelled theory */
   lazy val _name = _domain._name

   /** the body of this theory
    *  
    *  this is maintained lazily so that it can be built by the initializer of inheriting classes
    *  see the classes generated by [[uom.ScalaExporter]] for examples
    */
   private var _lazyBody : List[() => Unit] = Nil
   /** takes a argument and adds it to the _lazyBody */
   protected def realizes(b: => Unit) {
      _lazyBody ::= (() => b)
   }
   /**
    * creates the actual body of this class from the body
    */
   def init {
      try {
         _lazyBody.reverseMap {b => b()}
      } finally {
         _lazyBody = Nil // make sure, nothing gets added twice
      }
   }

   realizes {add(symbols.PlainInclude(_path, path))}
   
   /**
    * adds a [[RuleConstant]] realizing r.head as r to this model
    * @param r a BreadthRule for n-ary operators and an AbbrevRule for nullary operators
    */
   def rule(r: Rule) {
      val rc = {
        val name = r match {
          case r: SyntaxDrivenRule => r.head.name
          case r => LocalName(r.className)
        }
        new symbols.RuleConstant(toTerm, name, r)
      }
      add(rc)
   }
  
   private var _axioms: List[(String, () => Term, Term => Boolean)] = Nil
   def _assert(name: String, term: () => Term, assertion: Term => Boolean) {_axioms ::= ((name, term, assertion))}
   def _test(controller: frontend.Controller, log: String => Unit) {
      _axioms.foreach {
         case (n, tL, a) =>
           log("test case " + n)
           try {
             val t = tL()
             //log("term: " + controller.presenter.asString(t))
             val tS = controller.simplifier(t, Context(_path))
             //log("simplified: " + controller.presenter.asString(tS))
             val result = a(tS)
             log((if (result) "PASSED" else "FAILED") + "\n")
           } catch {
             case Unimplemented(f) => log("unimplemented " + f + "\n")
             case e: Error => log("error :" + e.toString + "\n")
           }
      }
   }
}

/**
 * mixes helper functions into a [[RealizationInScala]] that allow creating rules conveniently
 */
trait RuleCreators {
   def rule(r: Rule): Unit
   private val invertTag = "invert"
   
   /**
    * adds a rule for implementing a type
    */
   def universe(rt: RealizedType) {
      rule(rt)
   }
   def universe(synType: GlobalName)(rt: RealizedType) {
      universe(rt)
   }
   /** adds a rule for implementing a nullary symbol */
   def function(op:GlobalName, rType: RealizedType)(comp: rType.univ) {
      val ar = new AbbrevRule(op, rType(comp))
      rule(ar)
      
      val inv = new InverseOperator(op / invertTag) {
         def unapply(l: OMLIT) = {
            if (l == rType(comp)) Some((Nil))
            else None
         }
      }
      rule(inv)
   }

   /** typed variant, experimental, not used by ScalaExporter yet */
   def functionT[U,V](op:GlobalName, argType1: RepresentedRealizedType[U], rType: RepresentedRealizedType[V])(comp: U => V) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(argType1)
         val retType = rType
         def apply(args: List[Term]): OMLIT = args(0) match {
            case argType1(x) => rType(comp(x))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   
   /** adds a rule for implementing a unary symbol */
   def function(op:GlobalName, argType1: RealizedType, rType: RealizedType)(comp: argType1.univ => rType.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(argType1)
         val retType = rType
         def apply(args: List[Term]): OMLIT = args(0) match {
            case argType1(x) => rType(comp(x))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
  def function(op:GlobalName, argType1: RealizedType, argType2: RealizedType, rType: RealizedType)
            (comp: (argType1.univ, argType2.univ) => rType.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(argType1, argType2)
         val retType = rType
         def apply(args: List[Term]): OMLIT = (args(0), args(1)) match {
            case (argType1(x), argType2(y)) => rType(comp(x,y))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2)) match {
            case (t1(x1), t2(x2), t3(x3)) =>
               r(comp(x1,x2, x3))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4)) =>
               r(comp(x1,x2, x3, x4))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5)) =>
               r(comp(x1,x2, x3, x4, x5))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, t6: RealizedType,
             r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ, t6.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5, t6)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4), as(5)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5), t6(x6)) =>
               r(comp(x1,x2, x3, x4, x5, x6))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, t6: RealizedType,
             t7: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ, t6.univ, t7.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5, t6, t7)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4), as(5), as(6)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5), t6(x6), t7(x7)) =>
               r(comp(x1,x2, x3, x4, x5, x6, x7))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, t6: RealizedType,
             t7: RealizedType, t8: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ, t6.univ, t7.univ, t8.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5, t6, t7, t8)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4), as(5), as(6), as(7)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5), t6(x6), t7(x7), t8(x8)) =>
               r(comp(x1,x2, x3, x4, x5, x6, x7, x8))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, t6: RealizedType,
             t7: RealizedType, t8: RealizedType, t9: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ, t6.univ, t7.univ, t8.univ, t9.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5, t6, t7, t8, t9)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4), as(5), as(6), as(7), as(8)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5), t6(x6), t7(x7), t8(x8), t9(x9)) =>
               r(comp(x1,x2, x3, x4, x5, x6, x7, x8, x9))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }
   def function(op:GlobalName, t1: RealizedType, t2: RealizedType, t3: RealizedType, t4: RealizedType, t5: RealizedType, t6: RealizedType,
             t7: RealizedType, t8: RealizedType, t9: RealizedType, t10: RealizedType, r: RealizedType)
            (comp: (t1.univ, t2.univ, t3.univ, t4.univ, t5.univ, t6.univ, t7.univ, t8.univ, t9.univ, t10.univ)
                   => r.univ) {
      val ro = new RealizedOperator(op) {
         val argTypes = List(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10)
         val retType = r
         def apply(as: List[Term]): OMLIT = (as(0), as(1), as(2), as(3), as(4), as(5), as(6), as(7), as(8), as(9)) match {
            case (t1(x1), t2(x2), t3(x3), t4(x4), t5(x5), t6(x6), t7(x7), t8(x8), t9(x9), t10(x10)) =>
               r(comp(x1,x2, x3, x4, x5, x6, x7, x8, x9, x10))
            case _ => throw ImplementationError("illegal arguments")
         }
      }
      rule(ro)
   }

   /** the partial inverse of a unary operator */
   def inverse(op: GlobalName, argType: RealizedType, rType: RealizedType)(comp: rType.univ => Option[argType.univ]) {
      val inv = new InverseOperator(op / invertTag) {
         def unapply(l: OMLIT) = l match {
            case rType(y) => comp(y) match {
               case Some(x) => Some(List(argType(x)))
               case None => None
            }
            case _ => None
         }
      }
      rule(inv)
   }
   /** the partial inverse of a binary operator */
   def inverse(op: GlobalName, argType1: RealizedType, argType2: RealizedType, rType: RealizedType)
            (comp: rType.univ => Option[(argType1.univ,argType2.univ)]) {
      val inv = new InverseOperator(op / invertTag) {
         def unapply(l: OMLIT) = l match {
            case rType(y) => comp(y) match {
               case Some((x1,x2)) => Some(List(argType1(x1), argType2(x2)))
               case None => None
            }
            case _ => None
         }
      }
      rule(inv)
   }
}

trait TheoryScala {
   val _base : DPath
   val _name : LocalName
   lazy val _path = _base ? _name
}

trait ConstantScala {
   val parent: MPath
   val name: String
   lazy val path: GlobalName = parent ? name
   lazy val term = objects.OMID(path)
}

class UnaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg: Term) = path(arg)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a)) => Some(a)
      case _ => None
   }
}
class BinaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg1: Term, arg2: Term) = path(arg1, arg2)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a1, a2)) => Some((a1,a2))
      case _ => None
   }
}

trait ViewScala extends TheoryScala

object ConstantScala {
   implicit def constantToTerm(c: ConstantScala) = c.term
}

trait DocumentScala {
   private var realizations: List[RealizationInScala] = Nil
   private var documents : List[DocumentScala] = Nil
   def addRealization(r: RealizationInScala) {
      realizations ::= r
   }
   def addDocument(d: DocumentScala) {
      documents ::= d
   }
   def test(controller: frontend.Controller, log: String => Unit) {
      documents.foreach {_.test(controller, log)}
      realizations.foreach {_._test(controller, log)}
   }
}

