package info.kwarc.mmt.api.utils

import scala.util.parsing.json.JSONFormat

/**
 * straightforward API for JSON objects
 */
sealed abstract class JSON {
}

object JSONConversions {
   implicit def boolean2JSON(b: Boolean) = JSONBoolean(b)
   implicit def int2JSON(i: Int) = JSONInt(i)
   implicit def string2JSON(s: String) = JSONString(s)
}

case object JSONNull {
  override def toString = "null"
}

case class JSONInt(i: Int) extends JSON {
  override def toString = i.toString 
}

case class JSONBoolean(b: Boolean) extends JSON {
  override def toString = b.toString
}

case class JSONString(s: String) extends JSON {
  override def toString = "\"" + JSONFormat.quoteString(s) + "\""
}

case class JSONArray(values: JSON*) extends JSON {
  override def toString = values.mkString("[", ", ", "]")
}

case class JSONObject(map: (String,JSON)*) extends JSON {
  override def toString = map.map { case (k, v) => s"""${k.toString}: ${v.toString}"""}.mkString("{", ",\n", "}")
}