package fofsequa_to_sql
import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner.FofsequaReasoner
import org.nanquanu.fofsequa_reasoner.errors.Query_parse_exception
import org.nanquanu.fofsequa_sql.errors.Kb_exception

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}
import org.nanquanu.fofsequa_reasoner.eprover.QuotedString

import scala.collection.immutable.HashMap

object Topic_hierarchy {
  // Constructs a list of SQL queries for creating the required tables
  lazy val create_tables = Create_sql.create_tables(tables)

  val tables = List(
    Table("topic", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
      Field("parent_id", "INT"),
      Field("depth_in_tree", "INT NOT NULL"),
      Field("fofsequa_constant", "VARCHAR(255)"),
    ), Some("id")),

    Table("nq_project", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
      Field("description", "VARCHAR NOT NULL"),
      Field("fofsequa_constant", "VARCHAR(255)"),
    ), Some("id")),

    Table("project_interesting_to", List(
      Field("id", "INT NOT NULL"),
      Field("nq_project_id", "INT NOT NULL"),
      Field("topic_id", "INT NOT NULL"),
    ), Some("id")),
  )

  object Queries {
    val sub_field_of: Statement = FolseqParser.parse_statement_or_throw("![sub_field, parent from s_]: Sub_field_of(sub_field, parent)")
    val nq_project_with_parent: Statement = FolseqParser.parse_statement_or_throw("![nq_project, sub_field from s_]: Interesting_to(nq_project, sub_field)")
    val project_or_topic_with_name: Statement = FolseqParser.parse_statement_or_throw("![constant, name from s_]: Has_name(constant, name)")
    val project_with_description: Statement = FolseqParser.parse_statement_or_throw("![project, description from s_]: Project_description(project, description)")
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
    val constants_with_name = ("all", "All") :: (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.project_or_topic_with_name) match {
      case Success(value) => value
      case f: Failure[List[List[QuotedString]]] => return Failure(f.exception)
    })
      .map(get_text_of_first_two_elements)

    val project_with_description = (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.project_with_description) match {
      case Success(value) => value
      case f: Failure[List[List[QuotedString]]] => return Failure(f.exception)
    })
      .map(get_text_of_first_two_elements)

    // Create mapping from constant to name
    val name_of_constant = HashMap.from(constants_with_name)

    // Create mapping from project to description
    val description_of_project = HashMap.from(project_with_description)

    // Assign IDs to fields
    val id_of_field = assign_id(field_with_parent.map(
      { case (name, _parent) => name }
    ).distinct)

    // Assign tree depth to topics
    val topic_with_parent_and_depth: List[(String, Option[String], Int)] = {
      var depth_of = mutable.HashMap.empty[String, Int]
      var parent_of = mutable.HashMap.empty[String, Option[String]]

      // Has side effects
      def depth(topic: String): Int = {
        val result = depth_of.get(topic) match {
          case Some(value) => value
          case None => parent_of(topic) match {
            case Some(parent) => depth(parent) + 1
            case None => 0
          }
        }

        depth_of(topic) = result
        result
      }

      for((topic, parent) <- field_with_parent) {
        parent_of(topic) = parent
      }

      // Map with side effects
      field_with_parent.map({ case (topic, parent) => (topic, parent, depth(topic)) })
    }

    // Create queries to insert fields of interest
    val topic_queries = topic_with_parent_and_depth.map({case (field_name, parent_name, depth) => {
      val id = id_of_field(field_name)
      val name = name_of_constant.get(field_name) match {
        case Some(name) => name
        case None => return Failure  (Kb_exception(f"No name given for constant $field_name"))
      }

      val parent_id = parent_name match {
        case Some(name) => id_of_field(name)
        case None => "NULL"
      }

      // TODO: String interpolation can be used to perform an injection attack. Probably, the constructors of the KB can be trustued, but it's still better to start using prepared statements.
      s"""
        |INSERT INTO topic
        |VALUES ($id, '$name', $parent_id, $depth, '$field_name');""".stripMargin
    }})

    // Assign IDs to projects
    val projects = project_interesting_to.map(
      { case (project, _topic) => project }
    ).toSet

    val id_of_project = assign_id(projects)

    // Create queries to insert projects
    val project_queries = projects.map(project => {
      val id = id_of_project(project)

      val name = name_of_constant.get(project) match {
        case Some(name) => name
        case None => return Failure  (Kb_exception(f"No name given for project $project"))
      }

      val description = description_of_project.get(project) match {
        case Some(descr) => descr
        case None => return Failure(Kb_exception(f"No description given for project $project"))
      }

      s"""
         |INSERT INTO nq_project
         |VALUES ($id, '$name', '$description', '$project');""".stripMargin
    })

    var next_id = 0

    // Create queries to insert relations between NQ projects and Fields of interest
    val interesting_to_queries = project_interesting_to.map({case (project, topic) => {
      val project_id = id_of_project(project)
      val topic_id = id_of_field(topic)
      val id = next_id
      next_id += 1

      s"""
         |INSERT INTO project_interesting_to
         |VALUES ($id, $project_id, $topic_id);""".stripMargin
    }})

    Success(
      topic_queries ++
      project_queries ++
      interesting_to_queries
    )
  }
}

case class Topic(name: String, parent_name: String, id: Int)
case class Project_interesting_to(project_name: String, topic_name: String, id: Int)
