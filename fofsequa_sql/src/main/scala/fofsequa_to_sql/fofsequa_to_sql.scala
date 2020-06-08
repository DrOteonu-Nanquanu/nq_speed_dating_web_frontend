package fofsequa_to_sql
import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Fofsequa_to_sql {
  // Constructs a list of SQL queries for creating the required tables
  lazy val create_tables = tables.map( {
    case Table(table_name, fields, primary_key) =>
      s"CREATE TABLE $table_name" ++
        fields.map({case Field(field_name, data_type) =>
          field_name ++ " " ++ data_type
        }).mkString("(", ",", "") ++
        (primary_key match {
          case Some(key) => ", PRIMARY KEY (" ++ key ++ ")"
          case None => ""
      }) ++ ");"
  })

  val tables = List(
    Table("field_of_interest", List(
      Field("id", "INT"),
      Field("name", "VARCHAR(255) NOT NULL"),
      Field("parent_id", "INT"),
    )),

    Table("nq_project", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
    )),

    Table("project_interesting_to", List(
      Field("id", "INT NOT NULL"),
      Field("nq_project_id", "INT NOT NULL"),
      Field("field_of_interest_id", "INT NOT NULL"),
    )),
  )

  def sql_from_kb(file_name: String): Option[List[String]] = {
    val kb = FolseqParser.parseAll(FolseqParser.fofsequa_document, io.Source.fromFile(file_name).mkString) match {
      case FolseqParser.NoSuccess(msg, input) => { println(msg); return None }
      case FolseqParser.Success(parsed, _next) => parsed
    }

    // Create lists to hold sub_field and interesting_to relations
    var fois = ListBuffer[Field_of_interest]()
    var project_interesting_to = ListBuffer[Project_interesting_to]()
    var project_names = collection.mutable.Set[String]()
    var next_foi_id = 0
    var next_interesting_id = 0

    // Collect relevant data of all sub_field_of and interesting_to statements into the lists
    for(statement <- kb) {
      statement match {
        case AtomStatement(predicate, terms) => {
          if(terms.length == 2 && terms.forall(_.isInstanceOf[ConstantTerm])) {
            // This is a valid sub_field_of or field_of_interest statement
            val names = terms.map(_.asInstanceOf[ConstantTerm].constant.id.name)

            if(predicate.name.name == "Sub_field_of") {
              // format is: sub_field_of('child_name', 'parent_name')
              fois.append(Field_of_interest(
                names(0),
                names(1),
                next_foi_id
              ))

              next_foi_id += 1
            }
            else if(predicate.name.name == "Interesting_to") {
              // format is: interesting_to('project_name', 'field_of_interest_name')

              project_names.add(names(0))

              project_interesting_to.append(Project_interesting_to(
                names(0),
                names(1),
                next_interesting_id
              ))

              next_interesting_id += 1
            }
          }
          else {
            // TODO: error, incorrect format
          }
        }
        case _ => () // TODO: ERROR or ignore?
      }
    }

    // create maps of (name -> id)
    val projects_with_id = project_names.toList.zip(0 to project_names.size)

    val project_name_to_id = mutable.HashMap.empty[String, Int]
    val foi_name_to_id = mutable.HashMap.empty[String, Int]
    foi_name_to_id.update("all", -1)

    for((name, id) <- projects_with_id) {
      project_name_to_id.update(name, id)
    }

    for(Field_of_interest(name, _, id) <- fois) {
      foi_name_to_id.update(name, id)
    }

    // Create SQL insert statements
    val interesting_queries = project_interesting_to.map({case Project_interesting_to(project_name, foi_name, id) => {
      val project_id = project_name_to_id(project_name)
      val foi_id = foi_name_to_id(foi_name)

      s"""
         |INSERT INTO project_interesting_to
         |VALUES ($id, $project_id, $foi_id);""".stripMargin
    }})

    val foi_queries = fois.map({case Field_of_interest(name, parent_name, id) => {
      val parent_id = foi_name_to_id(parent_name)

      s"""
         |INSERT INTO field_of_interest
         |VALUES ($id, '$name', $parent_id);""".stripMargin
    }})

    val project_queries = projects_with_id.map({case (name, id) =>
      s"""
        |INSERT INTO nq_project
        |VALUES ($id, '$name');""".stripMargin
    })

    Some(
      (foi_queries ++
       interesting_queries ++
       project_queries
      ).toList
    )
  }
}

case class Field_of_interest(name: String, parent_name: String, id: Int)
case class Project_interesting_to(project_name: String, foi_name: String, id: Int)

case class Field(name: String, data_type: String)
case class Table(name: String, fields: Seq[Field], primary_key: Option[String] = Some("id"))