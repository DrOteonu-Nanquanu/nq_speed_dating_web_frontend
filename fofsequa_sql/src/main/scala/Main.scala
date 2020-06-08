object Main {
  def main(args: Array[String]): Unit = {
    println("Hello World!")

    var kb_file: Option[String] = None

    for(i <- 0 to args.length - 1) {
      if(args(i) == "--kb") {
        kb_file = Some(args(i + 1))
      }
    }

    kb_file match {
      case Some(file_name) => {
        val create_tables = fofsequa_to_sql.Fofsequa_to_sql.create_tables

        val db_contents = fofsequa_to_sql.Fofsequa_to_sql.sql_from_kb(file_name) match {
          case Some(value) => {
            println("succes")
            value
          }
          case None => {
            println("error")
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
