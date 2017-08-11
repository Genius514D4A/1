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

package system.basic

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import common.TestHelpers
import common.TestUtils
import common.rest.RestResult
import common.rest.WskRest
import common.WskProps
import common.WskTestHelpers
import spray.json._
import spray.json.DefaultJsonProtocol._
//import spray.json.pimpAny

import spray.http.StatusCodes.OK
import spray.http.StatusCodes.Conflict
import spray.http.StatusCodes.NotFound
import spray.http.StatusCodes.Unauthorized
import spray.http.StatusCodes.BadGateway
import spray.http.StatusCodes.Accepted
import spray.http.StatusCodes.Forbidden

@RunWith(classOf[JUnitRunner])
class WskRestBasicTests
    extends TestHelpers
    with WskTestHelpers {

    implicit val wskprops = WskProps()
    val wsk = new WskRest
    val defaultAction = Some(TestUtils.getTestActionFilename("hello.js"))

    behavior of "Wsk REST"

    it should "reject creating duplicate entity" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testDuplicateCreate"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, name, confirmDelete = false) {
                (action, _) => action.create(name, defaultAction, expectedExitCode = Conflict.intValue)
            }
    }

    it should "reject deleting entity in wrong collection" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testCrossDelete"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }
            wsk.action.delete(name, expectedExitCode = Conflict.intValue)
    }

    it should "reject unauthenticated access" in {
        implicit val wskprops = WskProps("xxx") // shadow properties
        println(wskprops.authKey)
        val errormsg = "The supplied authentication is invalid"
        wsk.namespace.list(expectedExitCode = Unauthorized.intValue).
            respData should include(errormsg)
        wsk.namespace.get(expectedExitCode = Unauthorized.intValue).
            respData should include(errormsg)
    }

    behavior of "Wsk Package REST"

    it should "create, update, get and list a package" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "testPackage"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) =>
                    pkg.create(name, parameters = params, shared = Some(true))
                    pkg.create(name, update = true)
            }
            val stdout = wsk.pkg.get(name).respData
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (""""publish": true""")
            stdout should include regex (""""version": "0.0.2"""")
            wsk.pkg.list().respData should include(name)
    }

    it should "create, and get a package" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val packageName = "packageName"
            val actionName = "actionName"
            val packageAnnots = Map(
                "description" -> JsString("Package description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))
            val actionAnnots = Map(
                "description" -> JsString("Action description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.pkg, packageName) {
                (pkg, _) => pkg.create(packageName, annotations = packageAnnots)
            }

            wsk.action.create(packageName + "/" + actionName, defaultAction, annotations = actionAnnots)
            val result = wsk.pkg.get(packageName)
            val ns = wsk.namespace.whois()
            wsk.action.delete(packageName + "/" + actionName)

            result.getField("name") shouldBe packageName
            result.getField("namespace") shouldBe ns
            val annos = result.getFieldJsValue("annotations").toString
            annos should include regex (""""value":"Package description"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
            val action = result.getFieldListJsObject("actions")(0)
            RestResult.getField(action, "name") shouldBe actionName
            val annoAction = RestResult.getFieldJsValue(action, "annotations").toString
            annoAction should include regex (""""value":"Action description"""")
            annoAction should include regex (""""name":"paramName1"""")
            annoAction should include regex (""""description":"Parameter description 2"""")
            annoAction should include regex (""""name":"paramName1"""")
            annoAction should include regex (""""description":"Parameter description 2"""")
    }

    it should "create a package with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "package with spaces"

            val res = assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) => pkg.create(name)
            }

            res.exitCode shouldBe OK.intValue
    }

    it should "create a package, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "packageFields"
        val paramInput = Map("payload" -> "test".toJson)
        val successMsg = s"ok: got package $name, displaying field"

        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.pkg, name) {
                (pkg, _) => pkg.create(name, parameters = paramInput)
            }

            val expectedParam = JsObject("payload" -> JsString("test"))
            val ns = wsk.namespace.whois()
            val result = wsk.pkg.get(name, fieldFilter = Some("namespace"))
            result.getField("namespace") shouldBe "guest"
            result.getField("name") shouldBe name
            result.getField("version") shouldBe "0.0.1"
            result.getFieldJsValue("publish").toString shouldBe "false"
            result.getFieldJsValue("binding").toString shouldBe "{}"
            result.getField("invalid") shouldBe ""
    }

    it should "reject creation of duplication packages" in withAssetCleaner(wskprops) {
        val name = "dupePackage"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.pkg, name) {
            (pkg, _) => pkg.create(name)
        }

        val result = wsk.pkg.create(name, expectedExitCode = Conflict.intValue).respData
        result should include regex (""""error": "resource already exists"""")
    }

    it should "reject delete of package that does not exist" in {
        val name = "nonexistentPackage"
        val stderr = wsk.pkg.delete(name, expectedExitCode = NotFound.intValue).respData.toString
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject get of package that does not exist" in {
        val name = "nonexistentPackage"
        val stderr = wsk.pkg.get(name, expectedExitCode = NotFound.intValue).respData.toString
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    behavior of "Wsk Action REST"

    it should "create the same action twice with different cases" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            assetHelper.withCleaner(wsk.action, "TWICE") { (action, name) => action.create(name, defaultAction) }
            assetHelper.withCleaner(wsk.action, "twice") { (action, name) => action.create(name, defaultAction) }
    }

    it should "create, update, get and list an action" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "createAndUpdate"
            val file = Some(TestUtils.getTestActionFilename("hello.js"))
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, file, parameters = params)
                    action.create(name, None, parameters = Map("b" -> "B".toJson), update = true)
            }

            val stdout = wsk.action.get(name).respData
            stdout should not include regex(""""key": "a"""")
            stdout should not include regex(""""value": "A"""")
            stdout should include regex (""""key": "b""")
            stdout should include regex (""""value": "B"""")
            stdout should include regex (""""publish": false""")
            stdout should include regex (""""version": "0.0.2"""")
            wsk.action.list().respData should include(name)
    }

    it should "reject create of an action that already exists" in withAssetCleaner(wskprops) {
        val name = "dupeAction"
        val file = Some(TestUtils.getTestActionFilename("echo.js"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, file)
        }

        val stderr = wsk.action.create(name, file, expectedExitCode = Conflict.intValue).respData
        stderr should include regex (""""error": "resource already exists"""")
    }

    it should "reject delete of action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.delete(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject invocation of action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.invoke(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject get of an action that does not exist" in {
        val name = "nonexistentAction"
        val stderr = wsk.action.get(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "create, and invoke an action that utilizes a docker container" in withAssetCleaner(wskprops) {
        val name = "dockerContainer"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            // this docker image will be need to be pulled from dockerhub and hence has to be published there first
            (action, _) => action.create(name, None, docker = Some("openwhisk/example"))
        }

        val args = Map("payload" -> "test".toJson)
        val run = wsk.action.invoke(name, args)
        withActivation(wsk.activation, run) {
            activation =>
                activation.response.result shouldBe Some(JsObject(
                    "args" -> args.toJson,
                    "msg" -> "Hello from arbitrary C program!".toJson))
        }
    }

    it should "create, and invoke an action that utilizes dockerskeleton with native zip" in withAssetCleaner(wskprops) {
        val name = "dockerContainerWithZip"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            // this docker image will be need to be pulled from dockerhub and hence has to be published there first
            (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("blackbox.zip")), kind = Some("native"))
        }

        val run = wsk.action.invoke(name, Map())
        withActivation(wsk.activation, run) {
            activation =>
                activation.response.result shouldBe Some(JsObject(
                    "msg" -> "hello zip".toJson))
                activation.logs shouldBe defined
                val logs = activation.logs.get.toString
                logs should include("This is an example zip used with the docker skeleton action.")
                logs should not include ("XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX")
        }
    }

    it should "create, and invoke an action using a parameter file" in withAssetCleaner(wskprops) {
        val name = "paramFileAction"
        val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
        val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, file)
        }

        val expectedOutput = JsObject("payload" -> JsString("test"))
        val run = wsk.action.invoke(name, parameterFile = argInput)
        withActivation(wsk.activation, run) {
            activation => activation.response.result shouldBe Some(expectedOutput)
        }
    }

    it should "create an action, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "actionFields"
        val paramInput = Map("payload" -> "test".toJson)
        val successMsg = s"ok: got action $name, displaying field"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, defaultAction, parameters = paramInput)
        }

        val expectedParam = JsObject("payload" -> JsString("test"))
        val ns = wsk.namespace.whois()

        val result = wsk.action.get(name)
        result.getField("name") shouldBe name
        result.getField("namespace") shouldBe "guest"
        result.getFieldJsValue("publish").toString shouldBe "false"
        result.getField("version") shouldBe "0.0.1"
        result.getFieldJsValue("exec").toString should include regex (""""kind":"nodejs:6","code":""")
        result.getFieldJsValue("parameters").toString should include regex (""""key":"payload","value":"test"""")
        result.getFieldJsValue("annotations").toString should include regex (""""key":"exec","value":"nodejs:6"""")
        result.getFieldJsValue("limits").toString should include regex (""""timeout":60000,"memory":256,"logs":10""")
        result.getField("invalid") shouldBe ""
    }

    /**
     * Tests creating an action from a malformed js file. This should fail in
     * some way - preferably when trying to create the action. If not, then
     * surely when it runs there should be some indication in the logs. Don't
     * think this is true currently.
     */
    it should "create and invoke action with malformed js resulting in activation error" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "MALFORMED"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("malformed.js")))
            }

            val run = wsk.action.invoke(name, Map("payload" -> "whatever".toJson))
            withActivation(wsk.activation, run) {
                activation =>
                    activation.response.status shouldBe "action developer error"
                    // representing nodejs giving an error when given malformed.js
                    activation.response.result.get.toString should include("ReferenceError")
            }
    }

    it should "create and invoke a blocking action resulting in an application error response" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "applicationError"
            val strErrInput = Map("error" -> "Error message".toJson)
            val numErrInput = Map("error" -> 502.toJson)
            val boolErrInput = Map("error" -> true.toJson)

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("echo.js")))
            }

            Seq(strErrInput, numErrInput, boolErrInput) foreach { input =>
                val result = wsk.action.invoke(name, parameters = input, blocking = true, expectedExitCode = BadGateway.intValue)
                RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe input.toJson.asJsObject
                wsk.action.invoke(name, parameters = input, blocking = true, result = true, expectedExitCode = BadGateway.intValue).respBody shouldBe input.toJson.asJsObject
            }
    }

    it should "create and invoke a blocking action resulting in an failed promise" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "errorResponseObject"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("asyncError.js")))
            }

            val result = wsk.action.invoke(name, blocking = true, expectedExitCode = BadGateway.intValue)
            RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe JsObject("error" -> JsObject("msg" -> "failed activation on purpose".toJson))
    }

    it should "invoke a blocking action and get only the result" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "basicInvoke"
            assetHelper.withCleaner(wsk.action, name) {
                (action, _) => action.create(name, Some(TestUtils.getTestActionFilename("wc.js")))
            }

            val r = wsk.action.invoke(name, Map("payload" -> "one two three".toJson), blocking = true, expectedExitCode = OK.intValue)
            println(r)
            r.respData should include regex (""""count": 3""")
    }

    it should "create, and get an action summary" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "actionName"
            val annots = Map(
                "description" -> JsString("Action description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, defaultAction, annotations = annots)
            }

            val result = wsk.action.get(name)
            val ns = wsk.namespace.whois()

            result.getField("name") shouldBe name
            result.getField("namespace") shouldBe ns
            val annos = result.getFieldJsValue("annotations").toString
            annos should include regex (""""value":"Action description"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
    }

    it should "create an action with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "action with spaces"

            val res = assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, defaultAction)
            }

            res.exitCode shouldBe OK.intValue
    }

    it should "create an action, and invoke an action that returns an empty JSON object" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "emptyJSONAction"

            assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, Some(TestUtils.getTestActionFilename("emptyJSONResult.js")))
            }

            val result = wsk.action.invoke(name, blocking = true, expectedExitCode = OK.intValue)
            RestResult.getFieldJsObject(result.getFieldJsObject("response"), "result") shouldBe JsObject()
    }

    it should "create, and invoke an action that times out to ensure the proper response is received" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "sleepAction"
            val params = Map("payload" -> "100000".toJson)
            val allowedActionDuration = 120 seconds
            val res = assetHelper.withCleaner(wsk.action, name) {
                (action, _) =>
                    action.create(name, Some(TestUtils.getTestActionFilename("timeout.js")),
                        timeout = Some(allowedActionDuration))
                    action.invoke(name, parameters = params, expectedExitCode = Accepted.intValue)
            }

            res.asInstanceOf[RestResult].getField("activationId") should not be ""
    }

    it should "create, and get docker action get ensure exec code is omitted" in withAssetCleaner(wskprops) {
        val name = "dockerContainer"
        (wp, assetHelper) => assetHelper.withCleaner(wsk.action, name) {
            (action, _) => action.create(name, None, docker = Some("fake-container"))
        }

        val result = wsk.action.get(name)
        RestResult.getField(result.getFieldJsObject("exec"), "code")  shouldBe ""
    }

    behavior of "Wsk Trigger REST"

    it should "create, update, get, fire and list trigger" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "listTriggers"
            val params = Map("a" -> "A".toJson)
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name, parameters = params)
                    trigger.create(name, update = true)
            }
            val stdout = wsk.trigger.get(name).respData
            stdout should include regex (""""key": "a"""")
            stdout should include regex (""""value": "A"""")
            stdout should include regex (""""publish": false""")
            stdout should include regex (""""version": "0.0.2"""")

            val dynamicParams = Map("t" -> "T".toJson)
            val run = wsk.trigger.fire(name, dynamicParams)
            println("run is " + run)
            withActivation(wsk.activation, run) {
                activation =>
                    activation.response.result shouldBe Some(dynamicParams.toJson)
                    activation.duration shouldBe None
                    activation.end shouldBe None
            }

            val runWithNoParams = wsk.trigger.fire(name, Map())
            withActivation(wsk.activation, runWithNoParams) {
                activation =>
                    activation.response.result shouldBe Some(JsObject())
                    activation.duration shouldBe None
                    activation.end shouldBe None
            }

            wsk.trigger.list().respData should include(name)
    }

    it should "create, and get a trigger summary" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "triggerName"
            val annots = Map(
                "description" -> JsString("Trigger description"),
                "parameters" -> JsArray(
                    JsObject(
                        "name" -> JsString("paramName1"),
                        "description" -> JsString("Parameter description 1")),
                    JsObject(
                        "name" -> JsString("paramName2"),
                        "description" -> JsString("Parameter description 2"))))

            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name, annotations = annots)
            }

            val result = wsk.trigger.get(name)
            val ns = wsk.namespace.whois()
            result.getField("name") shouldBe name
            result.getField("namespace") shouldBe ns
            val annos = result.getFieldJsValue("annotations").toString
            annos should include regex (""""value":"Trigger description"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
            annos should include regex (""""name":"paramName1"""")
            annos should include regex (""""description":"Parameter description 2"""")
    }

    it should "create a trigger with a name that contains spaces" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "trigger with spaces"

            val res = assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }

            res.exitCode shouldBe OK.intValue
    }

    it should "create, and fire a trigger using a parameter file" in withAssetCleaner(wskprops) {
        val name = "paramFileTrigger"
        val file = Some(TestUtils.getTestActionFilename("argCheck.js"))
        val argInput = Some(TestUtils.getTestActionFilename("validInput2.json"))

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name)
        }

        val expectedOutput = JsObject("payload" -> JsString("test"))
        val run = wsk.trigger.fire(name, parameterFile = argInput)
        withActivation(wsk.activation, run) {
            activation => activation.response.result shouldBe Some(expectedOutput)
        }
    }

    it should "create a trigger, and get its individual fields" in withAssetCleaner(wskprops) {
        val name = "triggerFields"
        val paramInput = Map("payload" -> "test".toJson)
        val successMsg = s"ok: got trigger $name, displaying field"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name, parameters = paramInput)
        }

        val expectedParam = JsObject("payload" -> JsString("test"))
        val ns = wsk.namespace.whois()

        val result = wsk.trigger.get(name)
        result.getField("namespace") shouldBe ns
        result.getField("name") shouldBe name
        result.getField("version") shouldBe "0.0.1"
        result.getFieldJsValue("publish").toString shouldBe "false"
        result.getFieldJsValue("annotations").toString shouldBe "[]"
        result.getFieldJsValue("parameters").toString should include regex (""""key":"payload","value":"test"""")
        result.getFieldJsValue("limits").toString shouldBe "{}"
        result.getField("invalid") shouldBe ""
    }

    it should "create, and fire a trigger to ensure result is empty" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "emptyResultTrigger"
            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) => trigger.create(name)
            }

            val run = wsk.trigger.fire(name)
            withActivation(wsk.activation, run) {
                activation => activation.response.result shouldBe Some(JsObject())
            }
    }

    it should "reject creation of duplicate triggers" in withAssetCleaner(wskprops) {
        val name = "dupeTrigger"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, name) {
            (trigger, _) => trigger.create(name)
        }

        val stderr = wsk.trigger.create(name, expectedExitCode = Conflict.intValue).respData
        stderr should include regex (""""error": "resource already exists"""")
    }

    it should "reject delete of trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.delete(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject get of trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.get(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject firing of a trigger that does not exist" in {
        val name = "nonexistentTrigger"
        val stderr = wsk.trigger.fire(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    behavior of "Wsk Rule REST"

    it should "create rule, get rule, update rule and list rule" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "listRules"
            val triggerName = "listRulesTrigger"
            val actionName = "listRulesAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName) {
                (rule, name) =>
                    rule.create(name, trigger = triggerName, action = actionName)
            }

            // finally, we perform the update, and expect success this time
            wsk.rule.create(ruleName, trigger = triggerName, action = actionName, update = true)

            val stdout = wsk.rule.get(ruleName).respData
            stdout should include(ruleName)
            stdout should include(triggerName)
            stdout should include(actionName)
            stdout should include regex (""""version": "0.0.2"""")
            wsk.rule.list().respData should include(ruleName)
    }

    it should "create rule, get rule, ensure rule is enabled by default" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "enabledRule"
            val triggerName = "enabledRuleTrigger"
            val actionName = "enabledRuleAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName) {
                (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
            }

            val stdout = wsk.rule.get(ruleName).respData
            stdout should include regex (""""status":\s*"active"""")
    }

    it should "display a rule summary when --summary flag is used with 'wsk rule get'" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val ruleName = "mySummaryRule"
            val triggerName = "summaryRuleTrigger"
            val actionName = "summaryRuleAction"

            assetHelper.withCleaner(wsk.trigger, triggerName) {
                (trigger, name) => trigger.create(name)
            }
            assetHelper.withCleaner(wsk.action, actionName) {
                (action, name) => action.create(name, defaultAction)
            }
            assetHelper.withCleaner(wsk.rule, ruleName, confirmDelete = false) {
                (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
            }

            // Summary namespace should match one of the allowable namespaces (typically 'guest')
            val ns = wsk.namespace.whois()
            val result = wsk.rule.get(ruleName)
            result.getField("name") shouldBe ruleName
            result.getField("namespace") shouldBe ns
            result.getField("status") shouldBe "active"
    }

    it should "create a rule, and get its individual fields" in withAssetCleaner(wskprops) {
        val ruleName = "ruleFields"
        val triggerName = "ruleTriggerFields"
        val actionName = "ruleActionFields"
        val paramInput = Map("payload" -> "test".toJson)
        val successMsg = s"ok: got rule $ruleName, displaying field"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, triggerName) {
            (trigger, name) => trigger.create(name)
        }
        assetHelper.withCleaner(wsk.action, actionName) {
            (action, name) => action.create(name, defaultAction)
        }
        assetHelper.withCleaner(wsk.rule, ruleName) {
            (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
        }

        val ns = wsk.namespace.whois()
        val rule = wsk.rule.get(ruleName)
        rule.getField("namespace") shouldBe ns
        rule.getField("name") shouldBe ruleName
        rule.getField("version") shouldBe "0.0.1"
        rule.getField("status") shouldBe "active"
        val result = wsk.rule.get(ruleName)
        val trigger = result.getFieldJsValue("trigger").toString
        trigger should include (triggerName)
        trigger should not include (actionName)
        val action = result.getFieldJsValue("action").toString
        action should not include (triggerName)
        action should include (actionName)
    }

    it should "reject creation of duplicate rules" in withAssetCleaner(wskprops) {
        val ruleName = "dupeRule"
        val triggerName = "triggerName"
        val actionName = "actionName"

        (wp, assetHelper) => assetHelper.withCleaner(wsk.trigger, triggerName) {
            (trigger, name) => trigger.create(name)
        }
        assetHelper.withCleaner(wsk.action, actionName) {
            (action, name) => action.create(name, defaultAction)
        }
        assetHelper.withCleaner(wsk.rule, ruleName) {
            (rule, name) => rule.create(name, trigger = triggerName, action = actionName)
        }

        val stderr = wsk.rule.create(ruleName, trigger = triggerName, action = actionName, expectedExitCode = Conflict.intValue).respData
        stderr should include regex (""""error": "resource already exists"""")
    }

    it should "reject delete of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.delete(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject enable of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.enable(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject disable of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.disable(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject status of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.state(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject get of rule that does not exist" in {
        val name = "nonexistentRule"
        val stderr = wsk.rule.get(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    behavior of "Wsk Namespace REST"

    it should "return a list of exactly one namespace" in {
        val lines = wsk.namespace.list()
        lines.getBodyListString().length shouldBe 1
    }

    it should "list entities in default namespace" in {
        // use a fresh wsk props instance that is guaranteed to use
        // the default namespace
        val result = wsk.namespace.get(expectedExitCode = OK.intValue)(WskProps()).respData
        result should include(""""actions":""")
        result should include(""""rules":""")
        result should include(""""triggers":""")
        result should include(""""packages":""")
    }

    it should "not list entities with an invalid namespace" in {
        val namespace = "fakeNamespace"
        val stderr = wsk.namespace.get(Some(s"/${namespace}"), expectedExitCode = Forbidden.intValue).respData

        stderr should include(""""error": "The supplied authentication is not authorized to access this resource."""")
    }

    behavior of "Wsk Activation REST"

    it should "create a trigger, and fire a trigger to get its individual fields from an activation" in withAssetCleaner(wskprops) {
        (wp, assetHelper) =>
            val name = "activationFields"

            assetHelper.withCleaner(wsk.trigger, name) {
                (trigger, _) =>
                    trigger.create(name)
            }

            val ns = wsk.namespace.whois()
            val run = wsk.trigger.fire(name)
            withActivation(wsk.activation, run) {
                activation =>
                    val result = wsk.activation.getActivation(activation.activationId)
                    println(result)
                    result.getField("name") shouldBe name
                    result.getField("namespace") shouldBe ns
                    result.getFieldJsValue("publish").toString shouldBe "false"
                    result.getField("version") shouldBe "0.0.1"
                    result.getField("subject") shouldBe ns
                    result.getField("activationId") shouldBe activation.activationId
                    result.getField("start") should not be ""
                    result.getField("end") shouldBe ""
                    result.getField("duration") shouldBe ""
                    result.getFieldJsValue("annotations").toString shouldBe "[]"
            }
    }

    it should "reject get of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.getActivation(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject logs of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.activationLogs(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

    it should "reject result of activation that does not exist" in {
        val name = "0" * 32
        val stderr = wsk.activation.activationResult(name, expectedExitCode = NotFound.intValue).respData
        stderr should include regex (""""error": "The requested resource does not exist."""")
    }

}
