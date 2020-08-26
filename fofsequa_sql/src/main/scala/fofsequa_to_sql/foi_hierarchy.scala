package fofsequa_to_sql
import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner.FofsequaReasoner
import org.nanquanu.fofsequa_reasoner.errors.Query_parse_exception

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
import org.nanquanu.fofsequa_reasoner.eprover.QuotedString

object Foi_hierarchy {
  // Constructs a list of SQL queries for creating the required tables
  lazy val create_tables = Create_sql.create_tables(tables)

  val tables = List(
    Table("field_of_interest", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
      Field("parent_id", "INT"),
      Field("depth_in_tree", "INT NOT NULL"),
    ), Some("id")),

    Table("nq_project", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
    ), Some("id")),

    Table("project_interesting_to", List(
      Field("id", "INT NOT NULL"),
      Field("nq_project_id", "INT NOT NULL"),
      Field("field_of_interest_id", "INT NOT NULL"),
    ), Some("id")),
  )

  object Queries {
    val sub_field_of: Statement = FolseqParser.parse_statement_or_throw("![sub_field, parent from s_]: Sub_field_of(sub_field, parent)")
    val nq_project_with_parent: Statement = FolseqParser.parse_statement_or_throw("![nq_project, sub_field from s_]: Interesting_to(nq_project, sub_field)")
    val project_or_foi_with_name: Statement = FolseqParser.parse_statement_or_throw("![constant, name from s_]: Has_name(constant, name)")
  }

  private def assign_id[T](iterable: Iterable[T]): mutable.HashMap[T, Int] = {
    var result = mutable.HashMap.empty[T, Int]
    var next_id = 0

    for(item <- iterable) {
      result.update(item, next_id)
      next_id += 1
    }

    result
  }

  def sql_from_kb(file_name: String): Try[List[String]] = {
    // Read file
    val kb: String = {
      val file = scala.io.Source.fromFile(file_name)

      try file.getLines.mkString catch {
        case exception: Throwable => return Failure(exception)
      }
      finally file.close()
    }

    def get_text_of_first_two_elements(answer_tuple: List[QuotedString]): (String, String) = (answer_tuple(0).text, answer_tuple(1).text)

    // Query fields and their parents
    val field_with_parent = ("all", None) :: (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.sub_field_of) match {
      case Success(value) => value
      case f: Failure[List[List[QuotedString]]] => return Failure(f.exception)
    })
      .map(answer_tuple => (answer_tuple(0).text, Some(answer_tuple(1).text)))

    // Query nanquanu projects and the interested fields
    val project_interesting_to = (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.nq_project_with_parent) match {
      case Success(value) => value
      case f: Failure[List[List[QuotedString]]] => return Failure(f.exception)
    })
      .map(get_text_of_first_two_elements)

    // Query names associated with each constant
    val constant_with_names = (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.project_or_foi_with_name) match {
      case Success(value) => value
      case f: Failure[List[List[QuotedString]]] => return Failure(f.exception)
    })
      .map(get_text_of_first_two_elements)

    // Assign IDs to fields
    val id_of_field = assign_id(field_with_parent.map(
      { case (name, _parent) => name }
    ).distinct)

    // Assign tree depth to FoI's
    val foi_with_parent_and_depth: List[(String, Option[String], Int)] = {
      var depth_of = mutable.HashMap.empty[String, Int]
      var parent_of = mutable.HashMap.empty[String, Option[String]]

      // Has side effects
      def depth(foi: String): Int = {
        val result = depth_of.get(foi) match {
          case Some(value) => value
          case None => parent_of(foi) match {
            case Some(parent) => depth(parent) + 1
            case None => 0
          }
        }

        depth_of(foi) = result
        result
      }

      for((foi, parent) <- field_with_parent) {
        parent_of(foi) = parent
      }

      // Map with side effects
      field_with_parent.map({ case (foi, parent) => (foi, parent, depth(foi)) })
    }

    // Create queries to insert fields of interest
    val foi_queries = foi_with_parent_and_depth.map({case (field_name, parent_name, depth) => {
      val id = id_of_field(field_name)
      val parent_id = parent_name match {
        case Some(name) => id_of_field(name)
        case None => "NULL"
      }
      s"""
        |INSERT INTO field_of_interest
        |VALUES ($id, '$field_name', $parent_id, $depth);""".stripMargin
    }})

    // Assign IDs to projects
    val projects = project_interesting_to.map(
      { case (project, _foi) => project }
    ).toSet

    val id_of_project = assign_id(projects)

    // Create queries to insert projects
    val project_queries = projects.map(project_name => {
      val id = id_of_project(project_name)

      s"""
         |INSERT INTO nq_project
         |VALUES ($id, '$project_name');""".stripMargin
    })

    var next_id = 0

    // Create queries to insert relations between NQ projects and Fields of interest
    val interesting_to_queries = project_interesting_to.map({case (project_name, foi_name) => {
      val project_id = id_of_project(project_name)
      val foi_id = id_of_field(foi_name)
      val id = next_id
      next_id += 1

      s"""
         |INSERT INTO project_interesting_to
         |VALUES ($id, $project_id, $foi_id);""".stripMargin
    }})

    Success(
      foi_queries ++
      project_queries ++
      interesting_to_queries
    )
  }
}

case class Field_of_interest(name: String, parent_name: String, id: Int)
case class Project_interesting_to(project_name: String, foi_name: String, id: Int)