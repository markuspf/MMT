package info.kwarc.mmt.api.utils

import scala.xml._
import scala.reflect.runtime.universe._

/**
 * This class uses Scala reflection to parse XML into user-defined case classes.
 * 
 * The effect is that the classes encode the XML grammar, and the parser picks the corresponding class for each XML tag.
 * This is kind of the opposite of generating a parser, where the grammar is fixed and the classes are generated.  
 * 
 * @param pkg the full name of the package in which the case classes are declared
 * 
 * An XML node is parsed into an case class instance as follows
 *  * the case class whose name is the tag name is used (except that XML - is treated as Scala _)
 *  * the arguments to the class (technically: the single apply method of the companion object) are computed as follows:
 *   * k: String: the value of the attribute with key s, or the content of the child with tag k, ("" if neither present)
 *   * k: Int: accordingly
 *   * k: Boolean: accordingly (false if not present)
 *   * t: A where A is one the case classes: recursive call on the single child of the child with tag t
 *   * t: Option[A]: accordingly, but the child may be absent
 *   * t: List[A]: accordingly but the child may have any number of children
 *   * _a: A: recursive call on the first remaining child
 *   * children: List[A]: result of recursively processing all remaining children
 *   The type A does not have to be a case class, e.g., the elements in a list can have the tags of subclasses of A.
 */
class XMLToScala(pkg: String) {
   /** mirrors are used to evaluated reflected objects */
   private val m = runtimeMirror(getClass.getClassLoader)

   /** the Type of String */
   private val StringType = typeOf[String]
   /** It's non-trivial to construct Type programmatically. So we take them by reflecting Dummy */
   private case class Dummy(a: Int, b: Boolean, c: List[Int], d: Option[Int])
   /** the argument types of Dummy */
   private val dummyTypes = typeOf[Dummy].companion.member(TermName("apply")).asMethod.paramLists.flatten.toList.map(_.asTerm.info)
   /** the Type of Int (strangely != typeOf[Int]) */
   private val IntType = dummyTypes(0)
   /** the Type of Boolean */
   private val BoolType = dummyTypes(1)
   /** matches the Type of a unary type operator */
   private class TypeRefMatcher(sym: Symbol) {
      def unapply(tp: Type): Option[Type] = {
         tp match {
            case tr: TypeRef if tr.sym == sym => Some(tr.args.head)
            case _ => None
         }
      }
   }
   /** matches the Type of List[A] */
   private object ListType extends TypeRefMatcher(dummyTypes(2).asInstanceOf[TypeRef].sym)
   /** matches the Type of List[A] */
   private object OptionType extends TypeRefMatcher(dummyTypes(3).asInstanceOf[TypeRef].sym)
   
   /** thrown on all errors, e.g., when expected classes are not present or XML nodes are not present */
   case class ExtractError(msg: String) extends Exception(msg)
   
   /** convert Scala id names to xml tag/key names */
   private def xmlName(s: String) = s.replace("_", "-")
   /** convert xml tag/key names to Scala id names */
   private def scalaName(s: String) = s.replace("-", "_")
   /** (non-recursively) remove comments and whitespace-only text nodes */
   private def cleanNodes(nodes: List[Node]) = nodes.filter {
      case _:Comment => false
      case Text(s) if s.trim == "" => false
      case _ => true
   }
   
   /** read and parse a file */
   def apply(file: File): Any = apply(xml.readFile(file))
   /** parse a Node */
   def apply(node: Node): Any = {
      val c = Class.forName(pkg + "." + scalaName(node.label))
      apply(node, m.classSymbol(c).toType)
   }

   /** parse a Node of expected Type tp */
   private def apply(node: Node, tp: Type): Any = {
      /* Type = Scala type of reflected Scala types
       * Symbol = reflection of a declaration
       * module = companion object
       */
      // the symbol of the companion object of tp
      val moduleSymbol = tp.typeSymbol.asClass.companion.asModule
      // the symbol of its apply method
      val applyMethodSymbol = tp.companion.member(TermName("apply")).asMethod
      // the arguments Name:Type of the apply methods, and the string representation of the Name
      val args: List[(Name, String, Type)] = applyMethodSymbol.paramLists.flatten.map {arg =>
         (arg.name, arg.name.decodedName.toString, arg.asTerm.info)
      }
      // the remaining children of node (removed once processed) 
      var children = cleanNodes(node.child.toList).zipWithIndex
      /** finds the string V by looking at (i) key="V" (ii) <key>V</key> (iii) "" */
      def getAttributeOrChild(scalaKey: String): String = {
         val key = xmlName(scalaKey)
         val att = xml.attr(node, key)
         if (att != "")
            att
         else {
            val keyChildren = getKeyedChild(key)
            keyChildren match {
               case Some(Text(s) :: Nil) => s
               case Some(nodes) if nodes.forall(_.isInstanceOf[SpecialNode]) => nodes.text // turn Text, EntityRef, Atom etc. into a single Text node
               case Some(nodes) => throw ExtractError(s"text node expected in child $key: $nodes")
               case None => ""
            }
         }
      }
      /** finds the nodes NODES by looking at (i) <label>NODES</label> (ii) None */
      def getKeyedChild(scalaKey: String): Option[List[Node]] = {
         val label = xmlName(scalaKey)
         val (child, i) = children.find {case (c,_) => c.label == label}.getOrElse {
            return None
         }
         children = children.filter(_._2 != i)
         Some(cleanNodes(child.child.toList))
      }
      
      /** compute argument values one by one depending on the needed type */
      val values = args.map {
         // base type: use getAttributeOrChild
         case (n, nS, StringType) =>
            getAttributeOrChild(nS)
         case (n, nS, IntType) =>
            val s = getAttributeOrChild(nS)
            try {s.toInt}
            catch {case _: Exception => throw ExtractError(s"integer expected at key $nS: $s")}
         case (n, nS, BoolType) =>
            val s = getAttributeOrChild(nS)
            s.toLowerCase match {
               case "true" => true
               case "false" | "" => false
               case b => throw ExtractError(s"boolean expected at key $nS: $b")
            }
         // special argument "children": all remaining nodes
         case (_, "children", _) =>
            children.map {c => apply(c._1)}
         // special argument _name: first remaining node
         case (_, nS, _) if nS.startsWith("_") =>
            val (child, _) = children.head
            children = children.tail
            apply(child)
         // default case: use getKeyedChild
         case (n, nS, argTp) =>
            lazy val wrongLength = ExtractError(s"no child with label $nS (of type $argTp) found in $node")
            lazy val noNode      = ExtractError(s"$nS does not have exactly one child (of type $argTp) in $node")
            var omitted = false
            val childNodes = getKeyedChild(nS).getOrElse {
               omitted = true
               Nil
            }
            // sub-split to handle special argument types
            argTp match {
               // List[A]
               case ListType(elemType) =>
                  if (omitted)
                     Nil
                  else
                     childNodes.map(apply(_))
               // Option[A]
               case OptionType(elemType) =>
                  if (omitted)
                     None
                  else if (childNodes.length == 1)
                     Some(apply(childNodes.head))
                  else
                     throw wrongLength
               // A
               case _ =>
                  if (omitted)
                     throw noNode
                  else if (childNodes.length != 1)
                     throw wrongLength
                  else
                     apply(childNodes.head)
            }
      }
      // evaluate moduleSymbol (to a singleton class) and get its runtime instance 
      val module = m.reflectModule(moduleSymbol).instance
      // evaluate the apply method of the instance
      val applyMethod = m.reflect(module).reflectMethod(applyMethodSymbol)
      // call the apply method on the values computed above
      val result = applyMethod(values:_*)
      //println("found: " + result)
      result
   }
}

// case classes for testing

case class A(a: String, b: Int, c: Boolean, d: B, e: List[B], f: Option[B], g: Option[C], children: List[C])
case class B(a: String, children: List[B])
abstract class C
case class Ca(a: String) extends C
case class Cb(_c1: C, _c2: C) extends C

// test cases
object Test {
   val n1 = <A a="a" b="1" c="true">
              <d><B><a>a</a></B></d>
              <e><B a="a1"/> <B a="a2"/></e>
              <g><Ca a="a"/></g>
              <Ca a="a"/>
              <Cb><Ca a="a0"/> <Ca a="a1"/></Cb>
            </A>
   val r1 = A("a", 1, true,
               B("a", Nil),
               List(B("a1",Nil), B("a2",Nil)),
               None,
               Some(Ca("a")),
               List(Ca("a"), Cb(Ca("a0"), Ca("a1")))
             )
   def main(args: Array[String]) {
      val a1 = new XMLToScala("info.kwarc.mmt.api.utils").apply(n1)
      println(a1)
      assert(a1 == r1)
   }
}