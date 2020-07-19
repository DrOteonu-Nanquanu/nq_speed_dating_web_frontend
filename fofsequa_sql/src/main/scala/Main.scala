import scala.io.StdIn
import scala.util.{Failure, Success}

object Fofsequa_sql {
  def main(args: Array[String]): Unit = {
    var kb_file: Option[String] = None
    var has_args = false

    for(i <- 0 to args.length - 1) {
      if(args(i) == "--kb") {
        kb_file = Some(args(i + 1))
      }
      if(args(i) == "--create_config") {
        has_args = true
        val path_to_eprover = StdIn.readLine("Where can the eprover executable be found?\n")
        org.nanquanu.fofsequa_reasoner.eprover.Eprover.create_config_file(path_to_eprover)
      }
    }

    kb_file match {
      case Some(file_name) => {
        val create_tables = fofsequa_to_sql.Foi_hierarchy.create_tables

        val db_contents = fofsequa_to_sql.Foi_hierarchy.sql_from_kb(file_name) match {
          case Success(value) => {
            // println("succes")
            value
          }
          case Failure(exception) => {
            println("error while creating database contents")
            throw exception
            return
          }
        }

        for(statement <- create_tables) {
          println(statement)
        }
        for(statement <- db_contents) {
          println(statement)
        }
      }
      case None => ()
    }
  }
}
