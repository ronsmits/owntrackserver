package exposed

import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("DB")

object DB {
    var databasePath = "jdbc:h2:tcp://domoticz.home/~/test"
    val db by lazy {
        logger.info("init db")
        Database.connect(databasePath, "org.h2.Driver")
    }

    fun setDbPath(dbpath: String) {
        logger.info("setting databasePath to $dbpath")
        databasePath = dbpath
    }
}
