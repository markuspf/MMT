package info.kwarc.mmt.api.checking

import info.kwarc.mmt.api._
import objects._
import symbols._
import modules._
import frontend._

/**
 * the primary object level checker of MMT
 * 
 * It checks [[CheckingUnit]]s by invoking [[Solver]]s and updates the checked objects with the solutions.
 * It manages errors and dependencies.  
 */
class RuleBasedChecker extends ObjectChecker {
   override val logPrefix = "object-checker"

   def apply(cu: CheckingUnit, rules: RuleSet)(implicit env: CheckingEnvironment) = {
      log("checking unit " + cu.component.getOrElse("without URI") + ": " + cu.judgement.present(o => controller.presenter.asString(o)))
      // if a component is given, we perform side effects on it
      val updateComponent = cu.component map {comp =>
         controller.globalLookup.getComponent(comp) match {
            case tc: TermContainer =>
               tc.dependsOn.clear
               (comp,tc)
            case _ => throw ImplementationError("not a TermContainer")
         }
      }
      // ** checking **
      log("using " + rules.getAll.mkString(", "))
      val solver = new Solver(controller, cu, rules)
      val mayHold = logGroup {
         solver.applyMain
      }
      // if solved, we can substitute all unknowns; if not, we still substitute partially
      val psol = solver.getPartialSolution
      val remUnknowns = solver.getUnsolvedVariables 
      val subs = psol.toPartialSubstitution
      val t = parser.ParseResult(Context.empty,cu.judgement.context, cu.judgement.wfo).toTerm
      val tI = t ^? subs //fill in inferred values
      val tIS = SimplifyInferred(tI, rules, cu.context ++ remUnknowns) //substitution may have created simplifiable terms
      TermProperty.eraseAll(tIS) // reset term properties (whether a term is, e.g., simplified, depends on where it is used)
      val result = parser.ParseResult(remUnknowns, Context.empty, tIS).toTerm
      //now report results, dependencies, errors
      val solution = solver.getSolution
      val success = solver.checkSucceeded
      // ** logging and error reporting **
      if (success) {
         log("success")
         updateComponent foreach {case (comp, tc) =>
            solver.getDependencies foreach {d =>
               env.reCont(ontology.DependsOn(comp, d))
               tc.dependsOn += d
            }
         }
      } else {
         log("------------- failure " + (if (mayHold) " (not proved)" else " (disproved)"))
         logGroup {
            solver.logState(logPrefix)
            val errors = solver.getErrors
            errors foreach {e =>
               env.errorCont(InvalidUnit(cu, e.narrowDownError, cu.present(solver.presentObj)))
            }
            if (errors.isEmpty) {
               solver.getConstraints foreach {dc =>
                  val h = dc.history + "unresolved constraint"
                  env.errorCont(InvalidUnit(cu, h, cu.present(solver.presentObj)))
               }
            }
         }
      }
      // ** change management **
      updateComponent foreach {case (comp, tc) =>
         val changed = Some(result) != tc.analyzed
         tc.analyzed = result // set it even if unchanged so that dirty flag gets cleared
         if (! success)
            tc.setAnalyzedDirty // revisit failed declarations
         if (changed) {
            log("changed")
            controller.memory.content.notifyUpdated(comp) //TODO: this could be cleaner if taken care of by the onCheck method
         } else {
            log("not changed")
         }
      }
      CheckingResult(success, Some(psol))
   }
   /**
    * A Traverser that reduces all redexes introduced by solving unknowns.
    */
   private object SimplifyInferred extends Traverser[RuleSet] {
      def traverse(t: Term)(implicit con : Context, rules: RuleSet) : Term = {
         t match {
            case OMA(OMS(parser.ObjectParser.oneOf), uom.OMLiteral.OMI(i) :: args) =>
               Traverser(this, args(i.toInt))
            case _ if parser.SourceRef.get(t).isEmpty =>
               controller.simplifier(t, con, rules)
            case _ =>
               Traverser(this, t)
         }
      }
   }
}