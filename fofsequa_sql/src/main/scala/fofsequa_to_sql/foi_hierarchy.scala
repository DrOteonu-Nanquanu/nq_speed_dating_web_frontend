package fofsequa_to_sql
import org.nanquanu.fofsequa._
import org.nanquanu.fofsequa_reasoner.FofsequaReasoner
import org.nanquanu.fofsequa_reasoner.errors.Query_parse_exception

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.parallel.immutable
import scala.util.{Failure, Success, Try}

object Foi_hierarchy {
  // Constructs a list of SQL queries for creating the required tables
  lazy val create_tables = Create_sql.create_tables(tables)

  val tables = List(
    Table("field_of_interest", List(
      Field("id", "INT NOT NULL"),
      Field("name", "VARCHAR(255) NOT NULL"),
      Field("parent_id", "INT"),
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
    val sub_field_of = FolseqParser.parse_statement_or_throw("![sub_field, parent from s_]: Sub_field_of(sub_field, parent)")
    val nq_project_with_parent = FolseqParser.parse_statement_or_throw("![nq_project, sub_field from s_]: Interesting_to(nq_project, sub_field)")
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

    // Query fields and their parents
    val field_with_parent = (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.sub_field_of) match {
      case Success(value) => value
      case f: Failure[List[List[String]]] => return Failure(f.exception)
    })
    .map(answer_tuple => (answer_tuple(0), answer_tuple(1)))

    // Query nanquanu projects and the interested fields
    val project_interesting_to = (FofsequaReasoner.evaluate_to_answer_tuples(kb, Queries.nq_project_with_parent) match {
      case Success(value) => value
      case f: Failure[List[List[String]]] => return Failure(f.exception)
    })
    .map(answer_tuple => (answer_tuple(0), answer_tuple(1)))

    // Assign IDs to fields
    val id_of_field = assign_id(field_with_parent.map(
      { case (name, _parent) => name }
    ).toSet)

    // Create queries to insert fields of interest
    val foi_queries = field_with_parent.map({case (field_name, parent_name) => {
      val id = id_of_field(field_name)
      val parent_id = id_of_field(parent_name)

      s"""
        |INSERT INTO field_of_interest
        |VALUES ($id, '$field_name', $parent_id);""".stripMargin
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

  def sql_from_kb_old(file_name: String): Option[List[String]] = {
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