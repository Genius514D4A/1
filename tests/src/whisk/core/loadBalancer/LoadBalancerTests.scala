/*
 * Copyright 2015-2016 IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.loadBalancer

import scala.concurrent.duration.Duration
import scala.concurrent.duration.SECONDS
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfter
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import spray.http.StatusCodes.MethodNotAllowed
import spray.http.StatusCodes.NotFound
import spray.http.StatusCodes.OK
import spray.httpx.SprayJsonSupport.sprayJsonMarshaller
import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller
import spray.httpx.unmarshalling.BasicUnmarshallers
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.DefaultJsonProtocol.RootJsObjectFormat
import spray.json.JsObject
import spray.json.pimpAny
import spray.testkit.ScalatestRouteTest
import whisk.common.TransactionId
import whisk.core.connector.Message
import scala.concurrent.Future
import whisk.core.connector.LoadBalancerResponse
import whisk.utils.ExecutionContextFactory
import scala.concurrent.ExecutionContext
import whisk.core.entity.ActivationId
import whisk.core.entity.Subject

/**
 * Unit tests of the LoadBalancer service as a standalone component.
 * These tests exercise a fresh instance of the service object in memory -- these
 * tests do NOT communication with a whisk deployment.
 *
 *
 * @Idioglossia
 * "using Specification DSL to write unit tests, as in should, must, not, be"
 */
@RunWith(classOf[JUnitRunner])
class LoadBalancerTests
    extends FlatSpec
    with BeforeAndAfter
    with ScalatestRouteTest
    with Matchers
    with LoadBalancerService
    with BasicUnmarshallers {

    behavior of "LoadBalancer API"

    implicit val trid = TransactionId.dontcare
    implicit val routeTestTimeout = RouteTestTimeout(Duration(90, SECONDS))
    implicit val executionContext = new ExecutionContext {
        def execute(runnable: Runnable) {
            runnable.run()
        }
        def reportFailure(cause: Throwable) {}
    }

    override def actorRefFactory = null

    override def doPublish(component: String, msg: Message)(implicit transid: TransactionId) = {
        Future.successful(LoadBalancerResponse.id(msg.activationId))
    }

    override def getInvokerHealth: JsObject = {
        JsObject("invoker0" -> "up".toJson, "invoker1" -> "down".toJson)
    }

    it should "respond to a ping" in {
        Get("/ping") ~> ping ~> check {
            status === OK
            responseAs[String] === "pong"
        }
    }

    it should "return a MethodNotAllowed error for PUT requests to ping" in {
        Put("/ping") ~> sealRoute(routes) ~> check {
            status === MethodNotAllowed
            responseAs[String] === "HTTP method not allowed, supported methods: GET"
        }
    }

    it should "not handle request to invalid publish path" in {
        Get("/publish/foobar") ~> sealRoute(routes) ~> check {
            status should be(NotFound)
        }
    }

    it should "post" in {
        val msg = Message(TransactionId.dontcare, "", Subject(), ActivationId(), None)
        Post("/publish/whisk", msg) ~> routes ~> check {
            status === OK
            val response = responseAs[LoadBalancerResponse]
            response.id.get should be(msg.activationId)
        }
    }

    it should "get invoker health" in {
        Get("/invokers") ~> routes ~> check {
            status === OK
            val response = responseAs[JsObject]
            response.fields("invoker0") should be("up".toJson)
            response.fields("invoker1") should be("down".toJson)
        }
    }
}