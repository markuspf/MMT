package info.kwarc.mmt.api.test
import info.kwarc.mmt.api._
import frontend._
import objects._
import objects.Conversions

object Test {
   def main(args: Array[String]) {
      val controller = new Controller
      // term to string
      def tts(t: Obj) = controller.presenter.asString(t)
      args.foreach {s =>
         controller.handleLine("file " + s)
      }
      val log = List()
      log foreach {l => controller.handleLine("log+ " + l)}
      
      val ip = new info.kwarc.mmt.api.frontend.MMTInterpolator(controller)
      val mpath = Path.parseM("http://cds.omdoc.org/test-new?FOLExt", utils.mmt.mmtbase)
      controller.handle(SetBase(mpath))
      import ip._
      
      val prover = new proving.Prover(controller)

      val context = cont"[x: bool, y: bool]"
      val stack = Stack(Context(mpath) ++ context)
      val x = OMV("x")
      val y = OMV("y")
      
      val rules = checking.RuleBasedChecker.collectRules(controller, context)
      val goal = mmt"ded forall [z:univ] z=z"
      val apps = prover.applicable(goal)(stack, rules)
      apps foreach {a =>
         println(a.label + " : " + tts(a.apply()))
      }
   }
}