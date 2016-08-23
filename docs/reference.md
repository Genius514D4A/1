
# OpenWhisk system details

The following sections provide more details about the OpenWhisk system.

## OpenWhisk entities

### Namespaces and packages

OpenWhisk actions, triggers, and rules belong in a namespace, and optionally a package.

Packages can contain actions and feeds. A package cannot contain another package, so package nesting is not allowed. Also, entities do not have to be contained in a package.

In Bluemix, an organization+space pair corresponds to a OpenWhisk namespace. For example, the organization `BobsOrg` and space `dev` would correspond to the OpenWhisk namespace `/BobsOrg_dev`.

You can create your own namespaces if you're entitled to do so. The `/whisk.system` namespace is reserved for entities that are distributed with the OpenWhisk system.


### Fully qualified names

The fully qualified name of an entity is
`/namespaceName[/packageName]/entityName`. Notice that `/` is used to delimit namespaces, packages, and entities. Also, namespaces must be prefixed with a `/`.

For convenience, the namespace can be left off if it is the user's *default namespace*.

For example, consider a user whose default namespace is `/myOrg`. Following are examples of the fully qualified names of a number of entities and their aliases.

| Fully qualified name | Alias | Namespace | Package | Name |
| --- | --- | --- | --- | --- |
| `/whisk.system/cloudant/read` |  | `/whisk.system` | `cloudant` | `read` |
| `/myOrg/video/transcode` | `video/transcode` | `/myOrg` | `video` | `transcode` |
| `/myOrg/filter` | `filter` | `/myOrg` |  | `filter` |

You will be using this naming scheme when you use the OpenWhisk CLI, among other places.

### Entity names

The names of all entities, including actions, triggers, rules, packages, and namespaces, are a sequence of characters that follow the following format:

* The first character must be an alphanumeric character, a digit, or an underscore.
* The subsequent characters can be alphanumeric, digits, spaces, or any of the following: `_`, `@`, `.`, `-`.
* The last character can't be a space.

More precisely, a name must match the following regular expression (expressed with Java metacharacter syntax): `\A([\w]|[\w][\w@ .-]*[\w@.-]+)\z`.


## Action semantics

The following sections describe details about OpenWhisk actions.

### Statelessness

Action implementations should be stateless, or *idempotent*. While the system does not enforce this property, there is no guarantee that any state maintained by an action will be available across invocations.

Moreover, multiple instantiations of an action might exist, with each instantiation having its own state. An action invocation might be dispatched to any of these instantiations.

### Invocation input and output

The input to and output from an action is a dictionary of key-value pairs. The key is a string, and the value a valid JSON value.

### Invocation ordering of actions

Invocations of an action are not ordered. If the user invokes an action twice from the command line or the REST API, the second invocation might run before the first. If the actions have side effects, they might be observed in any order.

Additionally, there is no guarantee that actions will execute atomically. Two actions can run concurrently and their side effects can be interleaved. OpenWhisk does not ensure any particular concurrent consistency model for side effects. Any concurrency side effects will be implementation-dependent.

### At-most-once semantics

The system supports at-most-once invocation of actions.

When an invocation request is received, the system records the request and dispatches an activation.

The system returns an activation ID (in the case of a nonblocking invocation) to confirm that the invocation was received. Notice that even in the absence of this response (perhaps due to a broken network connection), it is possible that the invocation was received.

The system attempts to invoke the action once, resulting in one of the following four outcomes:
- *success*: the action invocation completed successfully.
- *application error*: the action invocation was successful, but the action returned an error value on purpose, for instance because a precondition on the arguments was not met.
- *action developer error*: the action was invoked, but it completed abnormally, for instance the action did not detect an exception, or a syntax error existed.
- *whisk internal error*: the system was unable to invoke the action.
The outcome is recorded in the `status` field of the activation record, as document in a following section.

Every invocation that is successfully received, and that the user might be billed for, will eventually have an activation record.


## Activation record

Each action invocation and trigger firing results in an activation record.

An activation record contains the following fields:

- *activationId*: The activation ID.
- *start* and *end*: Timestamps recording the start and end of the activation. The values are in [UNIX time format](http://pubs.opengroup.org/onlinepubs/9699919799/basedefs/V1_chap04.html#tag_04_15).
- *namespace* and `name`: The namespace and name of the entity.
- *logs*: An array of strings with the logs that are produced by the action during its activation. Each array element corresponds to a line output to `stdout` or `stderr` by the action, and includes the time and stream of the log output. The structure is as follows: ```TIMESTAMP STREAM: LOG_OUTPUT```.
- *response*: A dictionary that defines the keys `success`, `status`, and `result`:
  - *status*: The activation result, which might be one of the following values: "success", "application error", "action developer error", "whisk internal error".
  - *success*: Is `true` if and only if the status is `"success"`
- *result*: A dictionary that contains the activation result. If the activation was successful, this contains the value that is returned by the action. If the activation was unsuccessful, `result` contains the `error` key, generally with an explanation of the failure.


## JavaScript actions

### Function prototype

OpenWhisk JavaScript actions run in a Node.js runtime, currently version 6.2.0.

Actions written in JavaScript must be confined to a single file. The file can contain multiple functions but by convention a function called `main` must exist and is the one called when the action is invoked. For example, the following is an example of an action with multiple functions.

```
function main() {
    return { payload: helper() }
}
function helper() {
    return new Date();
}
```

The action input parameters are passed as a JSON object as a parameter to the `main` function. The result of a successful activation is also a JSON object but is returned differently depending on whether the action is synchronous or asynchronous as described in the following section.


### Synchronous and asynchronous behavior

It is common for JavaScript functions to continue execution in a callback function even after a return. To accommodate this, an activation of a JavaScript action can be *synchronous* or *asynchronous*.

A JavaScript action's activation is **synchronous** if the main function exits under one of the following conditions:

- The main function exits without executing a ```return``` statement.
- The main function exits by executing a ```return``` statement that returns any value *except* a Promise.

Here is an example of a synchronous action.

```
// an action in which each path results in a synchronous activation
function main(params) {
  if (params.payload == 0) {
     return;
  } else if (params.payload == 1) {
     return {payload: 'Hello, World!'};
  } else if (params.payload == 2) {
    return whisk.error();   // indicates abnormal completion
  }
}
```

A JavaScript action's activation is **asynchronous** if the main function exits by returning a Promise.  In this case, the system assumes that the action is still running, until the Promise has been fulfilled or rejected.
Start by instantiating a new Promise object and passing it a callback function. The callback takes two arguments, resolve and reject, which are both functions. All your asynchronous code goes inside that callback.


The following is an example on how to fulfill a Promise by calling the resolve function.

```
function main(args) {
     return new Promise(function(resolve, reject) {
       setTimeout(function() {
         resolve({ done: true });
       }, 100);
    })
 }
```

The following is an example on how to reject a Promise by calling the reject function.

```
function main(args) {
     return new Promise(function(resolve, reject) {
       setTimeout(function() {
         reject({ done: true });
       }, 100);
    })
 }
```

It is possible for an action to be synchronous on some inputs and asynchronous on others. The following is an example.

```
  function main(params) {
      if (params.payload) {
         // asynchronous activation
         return new Promise(function(resolve, reject) {
                setTimeout(function() {
                  resolve({ done: true });
                }, 100);
             })
      }  else {
         // synchronous activation
         return {done: true};
      }
  }
```

Notice that regardless of whether an activation is synchronous or asynchronous, the invocation of the action can be blocking or non-blocking.

### Additional SDK methods

The `whisk.invoke()` function invokes another action. It takes as an argument a dictionary that defines the following parameters:

- *name*: The fully qualified name of the action to invoke,
- *parameters*: A JSON object that represents the input to the invoked action. If omitted, defaults to an empty object.
- *apiKey*: The authorization key with which to invoke the action. Defaults to `whisk.getAuthKey()`.
- *blocking*: Whether the action should be invoked in blocking or non-blocking mode. Defaults to `false`, indicating a non-blocking invocation.
- *next*: An optional callback function to be executed when the invocation completes. If next is not supplied, `whisk.invoke()` returns a promise.

  The signature for `next` is `function(error, activation)`, where:

  - `error` is `false` if the invocation succeeded, and a *truthy* value (a value that translates to true when evaluated in a Boolean context) if it failed, usually a string that describes the error.
  - On errors, `activation` might be undefined, depending on the failure mode.
  - When defined, `activation` is a dictionary with the following fields:
    - *activationId*: The activation ID:
    - *result*: If the action was invoked in blocking mode: The action result as a JSON object, else `undefined`.

  If `next` is not provided, then `whisk.invoke()` returns a promise.
  - If the invocation fails, the promise will reject with an object describing the failed invocation. It will potentially have two fields:
    - *error*: An object describing the error - usually a string.
    - *activation*: An optional dictionary that may or may not be present depending on the nature of the invocation failure. If present, it will have the following fields:
      - *activationId*: The activation ID:
      - *result*: If the action was invoked in blocking mode: The action result as a JSON object, else `undefined`.
  - If the invocation succeeds, the promise will resolve with a dictionary describing the activation with fields *activationId* and *result* as described above.

  Below is an example of a blocking invocation that utilizes the returned promise:
  ```javascript
  whisk.invoke({
    name: 'myAction',
    blocking: true
  })
  .then(function (activation) {
      // activation completed successfully, activation contains the result
      console.log('Activation ' + activation.activationId + ' completed successfully and here is the result ' + activation.result);
  })
  .catch(function (reason) {
      console.log('An error has occured ' + reason.error);

      if(reason.activation) {
        console.log('Please check activation ' + reason.activation.activationId + ' for details.');
      } else {
        console.log('Failed to create activation.');
      }
  });
  ```

The `whisk.trigger()` function fires a trigger. It takes as an argument a JSON object with the following parameters:

- *name*: The fully qualified name of trigger to invoke.
- *parameters*: A JSON object that represents the input to the trigger. If omitted, defaults to an empty object.
- *apiKey*: The authorization key with which to fire the trigger. Defaults to `whisk.getAuthKey()`.
- *next*: An optional callback to be executed when the firing completes.

  The signature for `next` is `function(error, activation)`, where:

  - `error` is `false` if the firing succeeded, and a *truthy* value  if it failed, usually a string that describes the error.
  - On errors, `activation` might be undefined, depending on the failure mode.
  - When defined, `activation` is a dictionary with an `activationId` field that contains the activation ID.

  If `next` is not provided, then `whisk.trigger()` returns a promise.
  - If the trigger fails, the promise will reject with an object describing the error.
  - If the trigger succeeds, the promise will resolve with a dictionary with an `activationId` field containing the activation ID.

The `whisk.getAuthKey()` function returns the authorization key under which the action is running. Usually, you do not need to invoke this function directly because it is used implicitly by the `whisk.invoke()` and `whisk.trigger()` functions.

### JavaScript runtime environments

JavaScript actions are executed by default in a Node.js version 6.2.0 environment.  The 6.2.0 environment will also be used for an action if the `--kind` flag is explicitly specified with a value of 'nodejs:6' when creating/updating the action.
The following packages are available to be used in the Node.js 6.2.0 environment:

- apn v1.7.5
- async v1.5.2
- body-parser v1.15.1
- btoa v1.1.2
- cheerio v0.20.0
- cloudant v1.4.1
- commander v2.9.0
- consul v0.25.0
- cookie-parser v1.4.2
- cradle v0.7.1
- errorhandler v1.4.3
- express v4.13.4
- express-session v1.12.1
- gm v1.22.0
- log4js v0.6.36
- iconv-lite v0.4.13
- merge v1.2.0
- moment v2.13.0
- mustache v2.2.1
- nano v6.2.0
- node-uuid v1.4.7
- nodemailer v2.5.0
- oauth2-server v2.4.1
- pkgcloud v1.3.0
- process v0.11.3
- pug v2.0.0
- request v2.72.0
- rimraf v2.5.2
- semver v5.1.0
- sendgrid v3.0.11
- serve-favicon v2.3.0
- socket.io v1.4.6
- socket.io-client v1.4.6
- superagent v1.8.3
- swagger-tools v0.10.1
- tmp v0.0.28
- twilio v2.9.1
- watson-developer-cloud v1.12.4
- when v3.7.7
- ws v1.1.0
- xml2js v0.4.16
- xmlhttprequest v1.8.0
- yauzl v2.4.2

The Node.js version 0.12.14 environment will be used for an action if the `--kind` flag is explicitly specified with a value of 'nodejs' when creating/updating the action.
The following packages are available to be used in the Node.js 0.12.14 environment:

- apn v1.7.4
- async v1.5.2
- body-parser v1.12.0
- btoa v1.1.2
- cheerio v0.20.0
- cloudant v1.4.1
- commander v2.7.0
- consul v0.18.1
- cookie-parser v1.3.4
- cradle v0.6.7
- errorhandler v1.3.5
- express v4.12.2
- express-session v1.11.1
- gm v1.20.0
- jade v1.9.2
- log4js v0.6.25
- merge v1.2.0
- moment v2.8.1
- mustache v2.1.3
- nano v5.10.0
- node-uuid v1.4.2
- oauth2-server v2.4.0
- process v0.11.0
- request v2.60.0
- rimraf v2.5.1
- semver v4.3.6
- serve-favicon v2.2.0
- socket.io v1.3.5
- socket.io-client v1.3.5
- superagent v1.3.0
- swagger-tools v0.8.7
- tmp v0.0.28
- watson-developer-cloud v1.4.1
- when v3.7.3
- ws v1.1.0
- xml2js v0.4.15
- xmlhttprequest v1.7.0
- yauzl v2.3.1


## Docker actions

Docker actions run a user-supplied binary in a Docker container. The binary runs in a Docker image based on Ubuntu 14.04 LTD, so the binary must be compatible with this distribution.

The Docker skeleton is a convenient way to build OpenWhisk-compatible Docker images. You can install the skeleton with the `wsk sdk install docker` CLI command.

The main binary program must be located in `/action/exec` inside the container. The executable receives the input arguments via `stdin` and must return a result via `stdout`.

You may include any compilation steps or dependencies by modifying the `Dockerfile` included in the `dockerSkeleton`.

## REST API

All the capabilities in the system are available through a REST API. There are collection and entity endpoints for actions, triggers, rules, packages, activations, and namespaces.

These are the collection endpoints:

- `https://{BASE URL}/api/v1/namespaces`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/actions`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/triggers`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/rules`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/packages`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/activations`

The `{BASE URL}` is the OpenWhisk API hostname (for example, openwhisk.ng.bluemix.net, 172.17.0.1, and so on).

For the `{namespace}`, the character `_` can be used to specify the user's *default
namespace* (that is, email address).

You can perform a GET request on the collection endpoints to fetch a list of entities in the collection.

There are entity endpoints for each type of entity:

- `https://{BASE URL}/api/v1/namespaces/{namespace}`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/actions/[{packageName}/]{actionName}`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/triggers/{triggerName}`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/rules/{ruleName}`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/packages/{packageName}`
- `https://{BASE URL}/api/v1/namespaces/{namespace}/activations/{activationName}`

The namespace and activation endpoints support only GET requests. The actions, triggers, rules, and packages endpoints support GET, PUT, and DELETE requests. The endpoints of actions, triggers, and rules also support POST requests, which are used to invoke actions and triggers and enable or disable rules. Refer to the [API reference](http://petstore.swagger.io/?url=https://raw.githubusercontent.com/openwhisk/openwhisk/master/core/controller/src/main/resources/whiskswagger.json) for details.

All APIs are protected with HTTP Basic authentication. The Basic authentication credentials are in the `AUTH` property in your `~/.wskprops` file, delimited by a colon. You can also retrieve these credentials in the [CLI configuration steps](../README.md#setup-cli).

The following is an example that uses the cURL command to get the list of all packages in the `whisk.system` namespace:

```
$ curl -u USERNAME:PASSWORD https://openwhisk.ng.bluemix.net/api/v1/namespaces/whisk.system/packages
```
```
[
  {
    "name": "slack",
    "binding": false,
    "publish": true,
    "annotations": [
      {
        "key": "description",
        "value": "Package that contains actions to interact with the Slack messaging service"
      }
    ],
    "version": "0.0.9",
    "namespace": "whisk.system"
  },
  ...
]
```

The OpenWhisk API supports request-response calls from web clients. OpenWhisk responds to `OPTIONS` requests with Cross-Origin Resource Sharing headers. Currently, all origins are allowed (that is, Access-Control-Allow-Origin is "`*`") and Access-Control-Allow-Headers yield Authorization and Content-Type.

**Attention:** Because OpenWhisk currently supports only one key per account, it is not recommended to use CORS beyond simple experiments. Your key would need to be embedded in client-side code, making it visible to the public. Use with caution.

## System limits

### Actions
OpenWhisk has a few system limits, including how much memory an action uses and how many action invocations are allowed per hour. The following table lists the default limits for actions.

| limit | description | configurable | unit | default |
| ----- | ----------- | ------------ | -----| ------- |
| timeout | a container is not allowed to run longer than N milliseconds | per action |  milliseconds | 60000 |
| memory | a container is not allowed to allocate more than N MB of memory | per action | MB | 256 |
| logs | a container is not allowed to write more than N MB to stdout | per action | MB | 10 |
| concurrent | no more than N concurrent activations per namespace are allowed | per namespace | number | 100 |
| minuteRate | a user cannot invoke more than this many actions per minute | per user | number | 120 |
| hourRate | a user cannot invoke more than this many actions per hour | per user | number | 3600 |
| codeSize | the maximum size of the actioncode | not configurable, limit per action | MB | 48 |
| parameters | the maximum size of the paramters that can be attached | not configurable, limit per action/package/trigger | MB | 1 |

### Per action timeout (ms) (Default: 60s)
* The timeout limit N is in the range [100ms..300000ms] and is set per action in milliseconds.
* A user can change the limit when creating the action.
* A container that runs longer than N milliseconds is terminated.

### Per action memory (MB) (Default: 256MB)
* The memory limit M is in the range from [128MB..512MB] and is set per action in MB.
* A user can change the limit when creating the action.
* A container cannot have more memory allocated than the limit.

### Per action logs (MB) (Default: 10MB)
* The log limit N is in the range [0MB..10MB] and is set per action.
* A user can change the limit when creating or updating the action.
* Logs that exceed the set limit are truncated and a warning is added as the last output of the activation to indicate that the activation exceeded the set log limit.

### Per action artifact (MB) (Fixed: 48MB)
* The maximum code size for the action is 48MB.
* It is recommended for a JavaScript action to use a tool to concatenate all source code including dependencies into a single bundled file.

### Per activation payload size (MB) (Fixed: 1MB)
* The maximum POST content size plus any curried parameters for an action invocation or trigger firing is 1MB.

### Per namespace concurrent invocation (Default: 100)
* The number of activations that are currently processed for a namespace cannot exceed 100.
* The default limit can be statically configured by whisk in consul kvstore.
* A user is currently not able to change the limits.

### Invocations per minute/hour (Fixed: 120/3600)
* The rate limit N is set to 120/3600 and limits the number of action invocations in one minute/hour windows.
* A user cannot change this limit when creating the action.
* A CLI or API call that exceeds this limit receives an error code corresponding to HTTP status code `429: TOO MANY REQUESTS`.

### Size of the parameters (Fixed: 1MB)
* The size limit for the parameters on creating or updating of an action/package/trigger is 1MB.
* The limit cannot be changed by the user.
* An entity with too big parameters will be rejected on trying to create or update it.

### Per Docker action open files ulimit (Fixed: 64:64)
* The maximum number of open file is 64 (for both hard and soft limits).
* The docker run command use the argument `--ulimit nofile=64:64`.
* For more information about the ulimit for open files see the [docker run](https://docs.docker.com/engine/reference/commandline/run) documentation.

### Per Docker action processes ulimit (Fixed: 512:512)
* The maximum number of processes available to a user is 512 (for both hard and soft limits).
* The docker run command use the argument `--ulimit nproc=512:512`.
* For more information about the ulimit for maximum number of processes see the [docker run](https://docs.docker.com/engine/reference/commandline/run) documentation.

### Triggers

Triggers are subject to a firing rate per minute and per hour as documented in the table below.

| limit | description | configurable | unit | default |
| ----- | ----------- | ------------ | -----| ------- |
| minuteRate | a user cannot fire more than this many triggers per minute | per user | number | 60 |
| hourRate | a user cannot fire more than this many triggers per hour | per user | number | 720 |

### Triggers per minute/hour (Fixed: 60/720)
* The rate limit N is set to 60/720 and limits the number of triggers that may be fired in one minute/hour windows.
* A user cannot change this limit when creating the trigger.
* A CLI or API call that exceeds this limit receives an error code corresponding to HTTP status code `429: TOO MANY REQUESTS`.
