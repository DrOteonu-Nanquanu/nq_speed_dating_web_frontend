import org.scalatest.flatspec.AnyFlatSpec

class TestBase extends AnyFlatSpec {
  "test" should "succeed" in {
    sql_to_fofsequa.Sql_to_fofsequa.go()
  }
}
