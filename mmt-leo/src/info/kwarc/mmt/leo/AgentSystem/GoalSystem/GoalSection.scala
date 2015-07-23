package info.kwarc.mmt.leo.AgentSystem.GoalSystem

import info.kwarc.mmt.leo.AgentSystem.{Change, Section}
/*

/**
 * This trait capsules the formulas responsible for the formula manipulation of the
 * blackboard.
 *
 */
class GoalSection(goal:Goal) extends Section(blackboard) {
  override val logPrefix ="AndOrSection"

  /** this type of section only stores data which is a subtype of the AndOr tree type*/
  type ObjectType=Goal
  type PTType=ObjectType //Meaningful alias for object type

  var data:PTType = goal
  var changes: List[Change[_]] = List(new Change(goal,List("ADD")))


}


class FactSection(blackboard:GoalBlackboard,shapeDepth: Int) extends Section {
  type ObjectType = Facts
  var data = new Facts(blackboard:GoalBlackboard,shapeDepth: Int)
  var changes: List[Change[_]] = Nil
}
*/
