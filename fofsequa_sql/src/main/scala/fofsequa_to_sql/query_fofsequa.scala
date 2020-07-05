package fofsequa_to_sql

import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner._
import sun.reflect.generics.reflectiveObjects.NotImplementedException

import scala.util.{Failure, Success, Try}


object Query_fofsequa {
  def query(goal: String, kb: String): Try[_] = {
    val answer = FofsequaReasoner.evaluate_fofsequa(kb, goal) match {
      case Success(ans) => ans
      case Failure(exception) => return Failure(exception)
    }

    Failure(new NotImplementedException())
  }
}
