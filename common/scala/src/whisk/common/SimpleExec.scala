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

package whisk.common

import scala.sys.process.ProcessLogger
import scala.sys.process.stringSeqToProcess

/*
 * Utility methods for running commands.
 */
object SimpleExec extends Logging {
    def syncRunCmd(cmd: Seq[String])(implicit transid: TransactionId = TransactionId.dontcare): (String, String, Int) = {
        info(this, s"Running command: ${cmd.mkString(" ")}")
        val pb = stringSeqToProcess(cmd)
        var outs = List[String]()
        var errs = List[String]()
        val exitCode = pb ! ProcessLogger((str) => outs ::= str, (str) => errs ::= str)
        (outs.reverse.mkString("\n"), errs.reverse.mkString("\n"), exitCode)
    }
}
