package whisk.core.database

import org.lightcouch.CouchDbClient
import org.lightcouch.CouchDatabase
import org.lightcouch.{ Response => CouchResponse }
import org.lightcouch.{ View => CouchView }

import whisk.core.entity.DocInfo
import whisk.core.entity.DocId
import whisk.core.entity.DocRevision

object CouchDbProvider extends CouchDbLikeProvider[CouchView] {
    type Client = CouchDbClient
    type Database = CouchDatabase
    type Response = CouchResponse

    def mkClient(dbHost: String, dbPort: Int, dbUsername: String, dbPassword: String) = {
        new CouchDbClient("https", dbHost, dbPort, dbUsername, dbPassword)
    }

    def getDB(client: Client, dbName: String) : Database = {
        if(dbName != null && dbName.nonEmpty) {
            client.database(dbName, false)
        } else {
            null
        }
    }

    def saveInDB(doc: Document, db: Database) : Response = {
        db.save(doc)
    }

    def findInDB[D](docInfo: DocInfo, db: Database)(implicit manifest: Manifest[D]) : D = {
        val DType = manifest.runtimeClass.asInstanceOf[Class[D]]
        db.find(DType, docInfo.id(), docInfo.rev())
    }

    def allDocsInDB[D](db: Database)(implicit manifest: Manifest[D]) : Seq[D] = {
        import scala.collection.JavaConversions.asScalaBuffer
        val klass = manifest.runtimeClass.asInstanceOf[Class[D]]
        db.view("_all_docs").includeDocs(true).query(klass)
    }

    def updateInDB(doc: Document, db: Database) : Response = {
        db.update(doc)
    }

    def removeFromDB(docInfo: DocInfo, db: Database) : Response = {
        db.remove(docInfo.id(), docInfo.rev())
    }

    def obtainViewFromDB(table: String, db: Database, includeDocs: Boolean, descending: Boolean, reduce: Boolean, inclusiveEnd: Boolean) : CouchView = {
        db.view(table).includeDocs(includeDocs).descending(descending).reduce(reduce).inclusiveEnd(inclusiveEnd)
    }

    def mkDocInfo(response: Response) : DocInfo = {
        DocInfo(response)
    }

    def describeResponse(response: Response) : String = {
        if (response == null) {
            "undefined"
        } else {
            s"${response.getId}[${response.getRev}] err=${response.getError} reason=${response.getReason}"
        }
    }

    def validateResponse(response: Response) : Boolean = {
        require(response != null && response.getError == null && response.getId != null && response.getRev != null, "response not valid")
        true
    }

    def shutdownClient(client: Client) : Unit = {
        client.shutdown()
    }
}

object CouchDbViewProvider extends CouchDbLikeViewProvider[CouchView] {
    def limitView(view: CouchView, limit: Int) : CouchView = view.limit(limit)

    def skipView(view: CouchView, skip: Int) : CouchView = view.skip(skip)

    def withStartEndView(view: CouchView, startKey: List[Any], endKey: List[Any]) : CouchView = {
        import scala.collection.JavaConverters.seqAsJavaListConverter
        view.startKey(startKey.asJava).endKey(endKey.asJava)
    }

    def queryView[T](view: CouchView)(implicit manifest: Manifest[T]) : Seq[T] = {
        import scala.collection.JavaConversions.asScalaBuffer
        val klass = manifest.runtimeClass.asInstanceOf[Class[T]]
        view.query(klass)
    }
}
