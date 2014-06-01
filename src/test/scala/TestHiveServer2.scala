import java.io.File
import java.nio.file.Files
import java.sql.{Connection, DriverManager, SQLException}
import org.apache.commons.io.FileUtils
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hdfs.MiniDFSCluster
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.mapred.JobConf
import org.apache.hadoop.mapred.MiniMRCluster
import org.apache.hive.jdbc.HiveDriver
import org.apache.hive.service.cli.session.{HiveSessionHookContext, HiveSessionHook}
import org.apache.hive.service.server.HiveServer2

object TestHiveServer2 {
  val JdbcUrl = "jdbc:hive2://localhost"
  private val currentDir = new File(".").getCanonicalPath()
  FileUtils.deleteDirectory(new File(s"$currentDir/logs"))
  FileUtils.deleteDirectory(new File(s"$currentDir/metastore_db"))

  // This is making sure we are not picking up locally installed hadoop libraries and stay isolated
  System.setProperty("java.library.path","")
  System.setProperty("hadoop.log.dir", "logs/hadoop") // MAPREDUCE-2785
  System.setProperty("hadoop.security.group.mapping", "org.apache.hadoop.security.ShellBasedUnixGroupsMapping")
  System.setProperty("hadoop.home.dir", currentDir)

  DriverManager.registerDriver(new HiveDriver)

  private [this] var tempDirectories = List.empty[File]
  private [this] val conf = new Configuration()
  conf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, createTempDirectory("dfs_base"))
  conf.set("dfs.permissions", "false")
  conf.set("hadoop.security.authorization", "false")
  conf.set("hadoop.security.group.mapping", "org.apache.hadoop.security.ShellBasedUnixGroupsMapping")

  private [this] val miniDFS = new MiniDFSCluster.Builder(conf).build()

  private [this] val miniMR = new MiniMRCluster(
    1, // numTaskTrackers
    miniDFS.getFileSystem().getUri().toString(),
    1, // numTaskTrackerDirectories
    null, // racks
    null, // hosts
    new JobConf(conf))

  private [this] val jt = miniMR.createJobConf(new JobConf(conf)).get("mapred.job.tracker")
  private [this] val warehouseDir = "file" + File.pathSeparator + createTempDirectory("hive_warehouse")

  private [this] val hiveConf = new HiveConf(getClass())
  configureHive(hiveConf)
  // A design issue in HiveServer2 is preventing the hive config to be propagated to internal session object.
  // The session object is ignoring HiveServer2 config, and is reloading the session from scratch from hive-site.xml.
  // Which works on a Hive deployed on a system, but not in an "on the fly" instance like this one.
  // This hook, passed in the hadoop job config when a mapred task is fired, is propagating what we added to the config
  // to the session's config as well.
  hiveConf.set(HiveConf.ConfVars.HIVE_SERVER2_SESSION_HOOK.varname, classOf[TestHiveSessionHook].getCanonicalName())

  private [this] val server = new HiveServer2()
  server.init(hiveConf)
  server.start()

  private [this] def createTempDirectory(prefix: String) = {
    val resVal = Files.createTempDirectory(prefix).toFile
    tempDirectories = resVal :: tempDirectories
    resVal.getCanonicalPath
  }

  def waitForServerToBeReady() {
    var tries = 3
    val WaitTimeMs = 500

    while(tries > 0 && !isServerReady) {
      Thread.sleep(WaitTimeMs)

      tries -= 1
    }

    if (tries == 0) {
      throw new Exception("HiveServer2 does not seem to be starting after 3 tries and 1.5secs")
    }
  }

  private [this] def isServerReady = {
    var connection: Connection = null

    try {
      connection = createConnection
      true
    } catch {
      case e: SQLException =>
        e.printStackTrace()
        false
    }
    finally {
      if (connection != null)
        try { connection.close() }
        catch { case t: Throwable => () }
    }
  }

  def createConnection =
    DriverManager.getConnection(JdbcUrl, System.getProperty("user.name"), "")

  def close() {
    server.stop()
    for(tempDir <- tempDirectories) {
      FileUtils.deleteDirectory(tempDir)
    }
  }
  // Add configuration to default hive configuration, so Hive can talk to the in-memory hadoop cluster.
  def configureHive(conf: HiveConf) {
    //
    // Fix Hive configuration
    //

    conf.set(HiveConf.ConfVars.HADOOPJT.varname, jt)
    conf.set(HiveConf.ConfVars.METASTOREWAREHOUSE.varname, warehouseDir)
    // Hive still need to use a hadoop command line tool. This one bundled with the project is pointing to the
    // minimal hadoop client jars we are downloading through ivy in the extdep config.
    conf.set(HiveConf.ConfVars.HADOOPBIN.varname, s"$currentDir/hadoop")

    //
    // Fix timeouts
    //

    conf.set(HiveConf.ConfVars.HIVE_SERVER2_THRIFT_LOGIN_TIMEOUT.varname, "1200")
    // This is a nasty one: since DriverManager.setLoginTimeout() is static, anything setting it will
    // also set it for all the future connections made to hive... The stats aggregator is one of those.
    conf.set(HiveConf.ConfVars.HIVE_STATS_JDBC_TIMEOUT.varname, "1200")
  }

}

class TestHiveSessionHook extends HiveSessionHook {
  def run(ctx: HiveSessionHookContext) {
    TestHiveServer2.configureHive(ctx.getSessionConf)
  }
}
