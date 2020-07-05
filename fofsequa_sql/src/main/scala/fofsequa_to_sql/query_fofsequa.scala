package fofsequa_to_sql

import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner._


object Query_fofsequa {
  def query(goal: String, kb: String): Option[_] = {
    val answer = Main.evaluate_fofsequa(kb, goal) match {
      case Some(ans) => ans
      case None => return None
    }

    answer match {

    }
  }
}
