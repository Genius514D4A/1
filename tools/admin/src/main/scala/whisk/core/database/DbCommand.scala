/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.database

import java.io.File

import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Flow, Framing, Keep, Sink, Source, StreamConverters}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.output.CloseShieldOutputStream
import org.rogach.scallop.{ScallopConfBase, Subcommand}
import spray.json.{JsObject, JsonParser, ParserInput}
import whisk.common.{Logging, TransactionId}
import whisk.core.cli.{CommandError, CommandMessages, NoopTicker, ProgressTicker, Ticker, WhiskCommand}
import whisk.core.entity.{ByteSize, WhiskActivation, WhiskAuth, WhiskEntity}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.{classTag, ClassTag}
import scala.util.Properties
import whisk.core.entity.size._

class DbCommand extends Subcommand("db") with WhiskCommand {
  descr("work with dbs")

  val databases = Set("whisks", "activations", "subjects")

  val get = new Subcommand("get") {
    descr("get contents of database")

    val dbTypeMapping: Map[String, ClassTag[_ <: DocumentSerializer]] =
      Map(
        "whisks" -> classTag[WhiskEntity],
        "activations" -> classTag[WhiskActivation],
        "subjects" -> classTag[WhiskAuth])

    val database = trailArg[String](descr = s"database type. One of $databases")

    validate(database) { db =>
      if (databases.contains(db)) {
        Right(Unit)
      } else {
        Left(CommandMessages.invalidDatabase(db, databases))
      }
    }

    val docs = opt[Boolean](descr = "include document contents")

    val view = opt[String](descr = "the view in the database to get", argName = "VIEW")

    val out = opt[File](descr = "file to dump the contents to")

    def dbType: ClassTag[_ <: DocumentSerializer] = dbTypeMapping(database())

  }
  addSubcommand(get)

  def exec(cmd: ScallopConfBase)(implicit system: ActorSystem,
                                 logging: Logging,
                                 materializer: ActorMaterializer,
                                 transid: TransactionId): Future[Either[CommandError, String]] = {
    implicit val executionContext = system.dispatcher
    val result = cmd match {
      case `get` => getDBContents()
    }
    result
  }

  def getDBContents()(implicit system: ActorSystem,
                      logging: Logging,
                      materializer: ActorMaterializer,
                      transid: TransactionId,
                      ec: ExecutionContext): Future[Either[CommandError, String]] = {
    val ticker = createTicker()
    val outputSink = Flow[JsObject]
      .map(js => ByteString(js.compactPrint + Properties.lineSeparator))
      .via(tick(ticker))
      .toMat(createSink())(Keep.right)
    val store = DbCommand.createStore(get.dbType)

    val f = store.getAll[IOResult](outputSink)
    f.onComplete(_ => ticker.close())
    f.map {
      case (count, r) =>
        if (r.wasSuccessful)
          Right(get.out.map(CommandMessages.dbContentToFile(count, _)).getOrElse(""))
        else throw r.getError
    }
  }

  private def createSink() =
    get.out
      .map(f => FileIO.toPath(f.toPath))
      .getOrElse(StreamConverters.fromOutputStream(() => new CloseShieldOutputStream(System.out)))

  private def tick[T](ticker: Ticker) = {
    Flow[T].wireTap(Sink.foreach(_ => ticker.tick()))
  }

  private def createTicker() = {
    if (get.out.isDefined) new ProgressTicker else NoopTicker
  }
}

object DbCommand {
  def createStore[D <: DocumentSerializer](classTag: ClassTag[D])(
    implicit system: ActorSystem,
    logging: Logging,
    materializer: ActorMaterializer): StreamingArtifactStore = {
    implicit val tag = classTag
    getStoreProvider().makeStore[D]()
  }

  def getStoreProvider(): StreamingArtifactStoreProvider = {
    val storeClass = ConfigFactory.load().getString("whisk.spi.ArtifactStoreProvider") + "$"
    if (storeClass == CouchDbStoreProvider.getClass.getName)
      CouchDBStreamingStoreProvider
    else
      throw new IllegalArgumentException(s"Unsupported ArtifactStore $storeClass")
  }

  def createJSStream(file: File, maxLineLength: ByteSize = 10.MB): Source[JsObject, Future[IOResult]] = {
    //Use a large look ahead buffer as actions can be big
    FileIO
      .fromPath(file.toPath)
      .via(Framing.delimiter(ByteString("\n"), maxLineLength.toBytes.toInt))
      .map(bs => JsonParser(ParserInput.apply(bs.toArray)).asJsObject)
  }
}
