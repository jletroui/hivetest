import java.io.File
import org.specs2.mutable._
import scala.io.StdIn

class NGramSpec extends Specification with After {
  val dir = new File(".").getCanonicalPath

  "Hive" should {
    "Be able to run a map reduce job" in {
      TestHiveServer2.waitForServerToBeReady()

      using(TestHiveServer2.createConnection) { con =>

        val smt = con.createStatement()

        smt.executeUpdate("create table ngram (ngram string, year int, occurences int, documents int) row format delimited fields terminated by '\\t' stored as textfile")
        smt.executeUpdate(s"load data local inpath '$dir/googlebooks-eng-all-1gram-20120701-z-sample.tsv' overwrite into table ngram")

        using(smt.executeQuery("select ngram, SUM(documents) as total_documents from ngram group by ngram")) { rs =>
          List(
            {
              rs.next(); rs.getString(1)
            } -> rs.getInt(2), {
              rs.next(); rs.getString(1)
            } -> rs.getInt(2)
          ) mustEqual List(
            "zenith" -> 426197,
            "zooplankton" -> 24939
          )
        }
      }
    }
  }

  def after = TestHiveServer2.close()

  def using[CLOSEABLE <: { def close(): Unit }, B](closeable: CLOSEABLE)(action: CLOSEABLE => B): B =
    try {
      action(closeable)
    } finally {
      closeable.close()
    }
}
