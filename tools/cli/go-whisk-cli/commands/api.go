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

package commands

import (
    "errors"
    "fmt"
    "reflect"
    "strings"

    "../../go-whisk/whisk"
    "../wski18n"

    "github.com/fatih/color"
    "github.com/spf13/cobra"
)

//////////////
// Commands //
//////////////

var apiCmd = &cobra.Command{
    Use:   "api",
    Short: wski18n.T("work with APIs"),
}

var apiCreateCmd = &cobra.Command{
    Use:           "create API_PATH API_VERB ACTION",
    Short:         wski18n.T("create a new API"),
    SilenceUsage:  true,
    SilenceErrors: true,
    PreRunE:       setupClientConfig,
    RunE: func(cmd *cobra.Command, args []string) error {

        if whiskErr := checkArgs(args, 3, 3, "Api create",
            wski18n.T("An API path, an API verb, and an action name are required.")); whiskErr != nil {
            return whiskErr
        }

        api, err := parseApi(cmd, args)
        if err != nil {
            whisk.Debug(whisk.DbgError, "parseApi(%s, %s) error: %s\n", cmd, args, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to parse api command arguments: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return whiskErr
        }

        retApi, _, err := client.Apis.Insert(api, false)
        if err != nil {
            whisk.Debug(whisk.DbgError, "client.Apis.Insert(%#v, false) error: %s\n", api, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to create api: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_NETWORK,
                whisk.DISPLAY_MSG, whisk.NO_DISPLAY_USAGE)
            return whiskErr
        }

        fmt.Fprintf(color.Output,
            wski18n.T("{{.ok}} created api {{.path}} {{.verb}} for action {{.name}}\n{{.fullpath}}\n",
                map[string]interface{}{
                    "ok": color.GreenString("ok:"),
                    "path": api.GatewayRelPath,
                    "verb": api.GatewayMethod,
                    "name": boldString(api.Action.Name),
                    "fullpath": retApi.GatewayFullPath,
                }))
        return nil
    },
}

var apiUpdateCmd = &cobra.Command{
    Use:           "update API_PATH API_VERB ACTION",
    Short:         wski18n.T("update an existing API"),
    SilenceUsage:  true,
    SilenceErrors: true,
    PreRunE:       setupClientConfig,
    RunE: func(cmd *cobra.Command, args []string) error {

        if whiskErr := checkArgs(args, 3, 3, "Api update",
            wski18n.T("An API path, an API verb, and an action name are required.")); whiskErr != nil {
            return whiskErr
        }

        api, err := parseApi(cmd, args)
        if err != nil {
            whisk.Debug(whisk.DbgError, "parseApi(%s, %s) error: %s\n", cmd, args, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to parse api command arguments: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return whiskErr
        }

        retApi, _, err := client.Apis.Insert(api, true)
        if err != nil {
            whisk.Debug(whisk.DbgError, "client.Apis.Insert(%#v, %t, false) error: %s\n", api, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to update api: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_NETWORK,
                whisk.DISPLAY_MSG, whisk.NO_DISPLAY_USAGE)
            return whiskErr
        }

        fmt.Fprintf(color.Output,
            wski18n.T("{{.ok}} update api {{.path}} {{.verb}} for action {{.name}}\n{{.fullpath}}\n",
                map[string]interface{}{
                    "ok": color.GreenString("ok:"),
                    "path": api.GatewayRelPath,
                    "verb": api.GatewayMethod,
                    "name": boldString(api.Action.Name),
                    "fullpath": retApi.GatewayFullPath,
                }))
        return nil
    },
}

var apiGetCmd = &cobra.Command{
    Use:           "get API_PATH API_VERB",
    Short:         wski18n.T("get API"),
    SilenceUsage:  true,
    SilenceErrors: true,
    PreRunE:       setupClientConfig,
    RunE: func(cmd *cobra.Command, args []string) error {
        var err error

        if whiskErr := checkArgs(args, 2, 2, "Api get",
            wski18n.T("An API path and an API verb are required.")); whiskErr != nil {
            return whiskErr
        }

        api, err := parseApi(cmd, args)
        if err != nil {
            whisk.Debug(whisk.DbgError, "parseApi(%s, %s) error: %s\n", cmd, args, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to parse api command arguments: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return whiskErr
        }

        retApi, _, err := client.Apis.Get(api)
        if err != nil {
            whisk.Debug(whisk.DbgError, "client.Apis.Get(%s) error: %s\n", api.Id, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to get api: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.NO_DISPLAY_USAGE)
            return whiskErr
        }

        if flags.common.summary {
            printSummary(api)
        } else {
            fmt.Fprintf(color.Output,
                wski18n.T("{{.ok}} api for path {{.path}} verb {{.verb}}\n",
                    map[string]interface{}{
                        "ok": color.GreenString("ok:"),
                        "path": retApi.GatewayRelPath,
                        "verb": retApi.GatewayMethod,
                    }))
            printJSON(retApi)
        }

        return nil
    },
}

var apiDeleteCmd = &cobra.Command{
    Use:           "delete API_PATH API_VERB ACTION",
    Short:         wski18n.T("delete an API"),
    SilenceUsage:  true,
    SilenceErrors: true,
    PreRunE:       setupClientConfig,
    RunE: func(cmd *cobra.Command, args []string) error {

        if whiskErr := checkArgs(args, 3, 3, "Api get",
            wski18n.T("An API path, an API verb, and an action name are required.")); whiskErr != nil {
            return whiskErr
        }

        api, err := parseApi(cmd, args)
        if err != nil {
            whisk.Debug(whisk.DbgError, "parseApi(%s, %s) error: %s\n", cmd, args, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to parse api command arguments: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return whiskErr
        }

        _, err = client.Apis.Delete(api)
        if err != nil {
            whisk.Debug(whisk.DbgError, "client.Apis.Delete(%s) error: %s\n", api.Id, err)
            errMsg := fmt.Sprintf(
                wski18n.T("Unable to delete action: {{.err}}",
                    map[string]interface{}{"err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.NO_DISPLAY_USAGE)
            return whiskErr
        }

        fmt.Fprintf(color.Output,
            wski18n.T("{{.ok}} deleted api {{.path}} {{.verb}}\n",
                map[string]interface{}{
                    "ok": color.GreenString("ok:"),
                    "path": api.GatewayRelPath,
                    "verb": api.GatewayMethod,
                }))
        return nil
    },
}

var apiListCmd = &cobra.Command{
    Use:           "list",
    Short:         wski18n.T("list APIs"),
    SilenceUsage:  true,
    SilenceErrors: true,
    PreRunE:       setupClientConfig,
    RunE: func(cmd *cobra.Command, args []string) error {
        var err error

        // Is the API verb valid?
        if flags.api.verb != "" {
            if whiskErr, ok := IsValidApiVerb(flags.api.verb); !ok {
                return whiskErr
            }
        }

        apiListOptions := &whisk.ApiListOptions{
            whisk.ApiOptions{
                flags.api.action,
                flags.api.path,
                flags.api.verb,
            },
            flags.common.limit,
            flags.common.skip,
            false,
        }

        apis, _, err := client.Apis.List(apiListOptions)
        if err != nil {
            whisk.Debug(whisk.DbgError, "client.Apis.List(%#v) error: %s\n", apiListOptions, err)
            errMsg := wski18n.T("Unable to obtain the list of apis: {{.err}}",
                map[string]interface{}{"err": err})
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_NETWORK,
                whisk.DISPLAY_MSG, whisk.NO_DISPLAY_USAGE)
            return whiskErr
        }

        printList(apis)
        return nil
    },
}

/*
 * args[0] = API relative path
 * args[1] = API verb
 * args[2] = Optional.  Action name (may or may not be qualified with namespace and package name)
 */
func parseApi(cmd *cobra.Command, args []string) (*whisk.Api, error) {
    var err error
    var basepath string = "/"
    var apiname string = "/"

    // Is the API path valid?
    // FIXME MWD - Add check

    // Is the API verb valid?
    if whiskErr, ok := IsValidApiVerb(args[1]); !ok {
        return nil, whiskErr
    }

    // Is the specified action name valid?
    // FIXME MWD - validate action exists??
    var qName qualifiedName
    if (len(args) == 3) {
        qName = qualifiedName{}
        qName, err = parseQualifiedName(args[2])
        if err != nil {
            whisk.Debug(whisk.DbgError, "parseQualifiedName(%s) failed: %s\n", args[2], err)
            errMsg := fmt.Sprintf(
                wski18n.T("''{{.name}}' is not a valid action name: {{.err}}",
                    map[string]interface{}{"name": args[2], "err": err}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return nil, whiskErr
        }
        if (qName.entityName == "") {
            whisk.Debug(whisk.DbgError, "Action name '%s' is invalid\n", args[2])
            errMsg := fmt.Sprintf(
                wski18n.T("'{{.name}}' is not a valid action name.",
                    map[string]interface{}{"name": args[2]}))
            whiskErr := whisk.MakeWskErrorFromWskError(errors.New(errMsg), err, whisk.EXITCODE_ERR_GENERAL,
                whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
            return nil, whiskErr
        }
    }

    if ( len(flags.api.apiname) > 0 ) {
        apiname = flags.api.apiname
    }

    if ( len(flags.api.basepath) > 0 ) {
        basepath = flags.api.basepath
    }

    api := new(whisk.Api)
    api.Namespace = client.Config.Namespace
    api.GatewayRelPath = args[0]    // Maintain case as URLs may be case-sensitive
    api.GatewayMethod = strings.ToUpper(args[1])
    api.Action = new(whisk.ApiAction)
    api.Action.BackendUrl = "https://" + client.Config.Host + "/api/v1/namespaces/" + qName.namespace + "/actions/" + qName.entityName + "?blocking=true"
    api.Action.BackendMethod = "POST"
    api.Action.Name = qName.entityName
    api.Action.Namespace = qName.namespace
    api.Action.Auth = client.Config.AuthToken
    api.ApiName = apiname
    api.GatewayBasePath = basepath
    api.Id = api.Namespace+":"+api.GatewayBasePath

    whisk.Debug(whisk.DbgInfo, "Parsed api struct: %#v\n", api)
    return api, nil
}

func IsValidApiVerb(verb string) (error, bool) {
    // Is the API verb valid?
    if _, ok := whisk.ApiVerbs[strings.ToUpper(verb)]; !ok {
        whisk.Debug(whisk.DbgError, "Invalid API verb: %s\n", verb)
        errMsg := fmt.Sprintf(
            wski18n.T("'{{.verb}}' is not a valid API verb.  Valid values are: {{.verbs}}",
                map[string]interface{}{
                    "verb": verb,
                    "verbs": reflect.ValueOf(whisk.ApiVerbs).MapKeys()}))
        whiskErr := whisk.MakeWskError(errors.New(errMsg), whisk.EXITCODE_ERR_GENERAL,
            whisk.DISPLAY_MSG, whisk.DISPLAY_USAGE)
        return whiskErr, false
    }
    return nil, true
}

///////////
// Flags //
///////////

func init() {
    //apiCreateCmd.Flags().StringVarP(&flags.api.action, "action", "a", "", wski18n.T("`ACTION` to invoke when API is called"))
    apiCreateCmd.Flags().StringVarP(&flags.api.apiname, "apiname", "n", "", wski18n.T("API collection `NAME` (default NAMESPACE)"))
    apiCreateCmd.Flags().StringVarP(&flags.api.basepath, "basepath", "b", "", wski18n.T("The API `BASE_PATH` to which the API_PATH is relative"))
    //
    //apiUpdateCmd.Flags().StringVarP(&flags.api.action, "action", "a", "", wski18n.T("`ACTION` to invoke when API is called"))
    //apiUpdateCmd.Flags().StringVarP(&flags.api.path, "path", "p", "", wski18n.T("relative `PATH` of API"))
    //apiUpdateCmd.Flags().StringVarP(&flags.api.verb, "method", "m", "", wski18n.T("API `VERB`"))

    apiGetCmd.Flags().BoolVarP(&flags.common.summary, "summary", "s", false, wski18n.T("summarize API details"))
    //apiGetCmd.Flags().StringVarP(&flags.api.action, "action", "a", "", wski18n.T("`ACTION` to invoke when API is called"))
    //apiGetCmd.Flags().StringVarP(&flags.api.path, "path", "p", "", wski18n.T("relative `PATH` of API"))
    //apiGetCmd.Flags().StringVarP(&flags.api.verb, "method", "m", "", wski18n.T("API `VERB`"))

    apiListCmd.Flags().StringVarP(&flags.api.action, "action", "a", "", wski18n.T("`ACTION` to invoke when API is called"))
    apiListCmd.Flags().StringVarP(&flags.api.path, "path", "p", "", wski18n.T("relative `API_PATH` of API"))
    apiListCmd.Flags().StringVarP(&flags.api.verb, "method", "m", "", wski18n.T("API `API_VERB`"))
    apiListCmd.Flags().IntVarP(&flags.common.skip, "skip", "s", 0, wski18n.T("exclude the first `SKIP` number of actions from the result"))
    apiListCmd.Flags().IntVarP(&flags.common.limit, "limit", "l", 30, wski18n.T("only return `LIMIT` number of actions from the collection"))

    apiCmd.AddCommand(
        apiCreateCmd,
        apiUpdateCmd,
        apiGetCmd,
        apiDeleteCmd,
        apiListCmd,
    )
}
