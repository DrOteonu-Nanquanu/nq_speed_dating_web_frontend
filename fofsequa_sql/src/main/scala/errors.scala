package org.nanquanu.fofsequa_sql.errors

case class Kb_exception(msg: String) extends Exception {
  override def toString = msg
}