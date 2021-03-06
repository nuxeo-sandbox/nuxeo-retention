⚠️ WARNING ⚠️

This plugin is now deprecated: please use the official and supported [Nuxeo Retention Addon](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-retention). It was built when this addon didn't exist. The sources can be found on [Github](https://github.com/nuxeo/nuxeo-retention).

The retention feature implemented [in the platform](https://doc.nuxeo.com/nxdoc/nuxeo-retention-management/) itself  has names and IDs (schemas, vocabularies, ...) that are in conflict with this sandbox plugin. This means **they are totally not compatibles: Do not install both plugins on the same server**

<hr>

## nuxeo-retention Sandbox plugin

[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-retention-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-retention-master/)

<hr>


* [Principles and Concepts](#principles-and-concepts)
  * [Rules Definition](#rules-definition)
  * [The `Record` Facet](#the-record-facet)
* [How it works](#how-it-works)
  * [Rule Definition](#rule-definition)
    * [Static Configuration](#static-configuration)
    * [Dynamic Configuration](#dynamic-configuration)
  * [Attach a Rule](#attach-a-rule)
    * [Attach a Rule with Java API](#attach-a-rule-with-java-api)
    * [Attach a Rule with Automation](#attach-a-rule-with-automation)
	* [Attaching a Dynamic Rule from the UI](#attaching-a-dynamic-rule-from-the-ui)
	* [Detaching a Rule](#detaching-a-rule)
  * [Checking Rules to Start or End the Retention](#checking-rules-to-start-or-end-the-retention)
  * [Events Sent by the Plugin](#events-sent-by-the-plugin)
  * [Enforcing that a Document Under Active Retention Cannot Be Modified](#enforcing-that-a-document-under-active-retention-cannot-be-modified)
* [User Interface](#user-interface)
  * [Overriding Default Slots](#overriding-default-slots)
  * [Deployment](#deployment)
    * [`nuxeo-retention` folder](#nuxeo-retention-folder)
    * [`retentionconfiguration` folder](#retentionconfiguration-folder)
  * [Overriding the UI](#overriging-the-ui)
    * [Overriding the Actions Vocabularies](#overriding-the-actions-vocabularies)
* [Operations](#operations)
  * [`Retention.AttachRule`](#retention-attachrule)
  * [`Retention.BulkAttachRule`](#retention-bulkattachrule)
  * [`Retention.RemoveRules`](#retention-removerules)
  * [`Retention.BulkRemoveRule`](#retention-bulkremoverule)
  * [`Retention.CreateRule`](#retention-createrule)
* [Tuning the Plugin](*tuning-the-plugin)
* [TODO](#todo)
* [Build and Deploy](build-and-deploy)
* [Simple Test](#simple-test)
* [LICENSE](#license)


## Principles & Concepts

This plugin allows for defining retention rule(s) and attach the rule(s) to any number of documents. A document under active retention cannot be modified or deleted.

Rules allow for setting:

* Condition to start the retention, with or without delay
* Condition to end the retention, with or without a reminder
* Action(s) to run when the retention starts and ends (typically, lock the document, or when the retention ends, delete it, ...)

The plugin works out of the box, and the rules can be defined:

* Dynamically, using the `RetentionConfiguration` document (or any custom document having the `RetentionRule` facet)
* Statically, via XML configuration within Nuxeo Studio.

Once rules are defined, they can be attached to a single document or to a list of documents in a single action (Automation in Studio or API call in a Java plugin).

Also, several rules can be attached to a document. They will be evaluated and handled one after the other, in the order they were attached.

The plugin regularly (once/day by default - this can be overridden) checks:

* For document that will enter retention
* For document that need to exit retention

For each of these events and each of the document concerned by them, an event is triggered allowing for taking action accordingly with the application business rules (notify, archive, ...)

The plugin provides the UI elements to handle retention: Layouts for configuring dynamic rules, action button to select a rule to apply, and widget displaying the retention status of a document.

Please, see at the end this README the "TODO - Work in Progess" topic.



### Rules Definition

A rule can be defined:

- Statically, via an XML contribution (see below)
- Or dynamically, using a document storing the values in the `retention_rule` schema (prefix `rule`)
  - Note: Adding the `RetentionRule` facet to a document automatically adds the schema
  - For convenience, the plugin provides the `RetentionConfiguration` document type and its layouts, so you can use it to allow users for configuring dynamic rules.

Each rule is composed of:

 - An id
     - The unique name of an XML contribution (see below)
     - Or the UUID of a document defining the rules:
       - Must have the `retention_rule` schema
       - Note: Adding the `RetentionRule` facet to a document adds this schema
 - A condition for retention start:
     - event: A string, like `documentCreated`, `documentModified`, ...)
     - condition: A string, and `EL` expression (i.e: `document.getType()=='File'`, or `document.getPropertyValue('record:min_cutoff_at').before(currentDate)`, ...)
     - **WARNING**: This is EL. Not MVEL, not JavaScript, no FreeMarker, ...
 - A _begin_ action: *what we do after entering cutoff*
     - The ID (name) of and Automation Chain
     - Field: `rule:beginAction`
 - **Or** A list of pre-defined actions, each being an operation.
   - Field: `rule:beginActions` (plural)
   - The plugin provides the `RetentionBegin` vocabulary for this purpose
   - This vocabulary can be localized, and the ID of each item is the ID of the operation
   - NOTE: It is not possible to have both a single Automation chain _and_ a list of pre-defined operations
 - A condition for retention end
     - event: A string, like `documentMoved`
     - condition: A string, and `EL` expression (see above)
 - A `beginDelayPeriod`: Java period
 - The retention duration (a Java period)
 - A duration, in days for the reminder before end of retention. An event will be triggered when _(end of retention - reminderDays)_ is reached.
 - A retention end action : *what we do after entering cutoff*
    - Automation
    - The ID (name) of and Automation Chain
    - Field: `rule:endAction`
 - **Or** A list of predefined actions, each being an operation.
   - Field: `rule:endActions` (plural)
   - The plugin provides the `RetentionEnd` vocabulary
   - This vocabulary can be localized, and the ID of each item is the ID of the operation
   - NOTE: It is not possible to have both a single Automation chain _and_ a list of pre-defined operations

<p>&nbsp;</p>

### Record facet

When a retention rule is applied on the document we store the data into the `Record` facet that is added to each document under retention.
The facet holds the `record` schema:

   - Status of the retention (`record:status`: 'unmanaged', 'active', 'expired')
   - List of associated rule(s) (`record:rules`)
   - Retention start (target or actual): `record:min_cutoff_at`
   - Retention end  (target or actual) : `record:max_retention_at`
   - The`record:reminder_start_date` field: When this value is not null and is reached, the `retentionAboutToExpire` event is fired. Configuration can listen to this event and act accordingly ( mail notification, ...)

<p>&nbsp;</p>

## How it works

### Rule Definition

There are two ways to contribute retention rules:

- **Statically**, by contributing to the "rules" extension point in the RetentionService
- **Dynamically** via the `RetentionRule` facet: In this case, the id of the rule is the id of the document holding the facet. The facet comes with the `retention_rule` schema (prefix `rule`), which holds the values for the rule (see "Rules Definition").

#### Static configuration

These are the steps to contribute with new rules as an extension point:

- Access to Nuxeo Studio
- Browse to *CONFIGURATION > Advanced Settings > XML Extensions*
- Create a new *XML extension* called *RETENTION* (or whatever name you want to use)
- Add your rules. In this example we will add 2 rules:
  * Retain *File* document types for 1 year. Initially locked and deleted at the end of the period. Reminder sent 3 days before the end of the period.
  * Retain *Picture* document types for 3 months. Initially lock and then unlock and trash (not delete) at the end of the period. Reminder sent one week before the end of the period.

```xml
<extension target="org.nuxeo.ecm.retention.RetentionService" point="rules">

  <rule>
    <id>fileRetentionWithReminder</id>
    <begin-condition expression="document.getType()=='File'"></begin-condition>
    <begin-delay></begin-delay>
    <retention-duration>P1Y</retention-duration>
    <retention-reminder-days>3</retention-reminder-days>
    <begin-action>Document.Lock</begin-action>
    <end-action>Document.Delete</end-action>
    <end-condition expression=""></end-condition>
  </rule>

    <rule>
    <id>pictureRetentionWithReminder</id>
    <begin-condition expression="document.getType()=='Picture'"></begin-condition>
    <begin-delay></begin-delay>
    <retention-duration>P3M</retention-duration>
    <retention-reminder-days>7</retention-reminder-days>
    <begin-action>Document.Lock</begin-action>
    <end-actions>
      <action order="0" operation="Document.Unlock"></action>
      <action order="1" operation="Document.Trash"></action>
    </end-actions>
    <end-condition expression=""></end-condition>
  </rule>

</extension>
```

#### Dynamic Configuration
Just create and fill a document which has the `RetentionRule` facet. The plugin provides out of the box the `RetentionConfiguration` document type for this purpose, with its layouts for the UI, allowing for setting the different fields (begin action(s), end action(s), delays, duration, ...)

*See below "User Interface" topic.*

### Attach a Rule

As soon as a rule is attached to a document:

* The `Record` facet is added (if not already present)
* Evaluation of the rule is done immediately. So, for example, the document may immediately go to retention, because there is no delay specified in the rule.

##### Attach a Rule with Java API
The following APIs are exposed in the `RetentionService`:

- A method to attach a rule on a single document:     
`void attachRule(String ruleId, DocumentModel doc);`
- A method to attach a rule to a query result
void attachRule(String ruleId, String query, CoreSession session);`. This call uses Nuxeo's Bulk Action Framework, ready for scaling and handling a very large number of document

##### Attach a Rule with Automation
Also, an operation is provided: `Retention.AttachRule`. It has two syntaxes, depending on the input, allowing for attaching a rule to the input document or, when no input document is passed, to list of documents found using a NXQL query

_Attach rule to a single document_

* Input is the document a rule must be attached to
* `ruleId` is a string parameter, the ID of a rule to attach:
  * Either the if of static (XML) rule
  * Or the UID of a Document with the `RetentionRule` facet
* `nxql` parameter is ignored if passed
* * Returns the modified document

_Attach rule to a several documents_

* Input is `null`
* `ruleId` is a string parameter, the ID of a rule to attach:
  * Either the if of static (XML) rule
  * Or the UID of a Document with the `RetentionRule` facet
* `nxql` is a string parameter, the NXQL to perform to find the retention to put under retention. For example:

```
SELECT * From Document WHERE
       ecm:path STARTSWITH '/some/folder/'
   AND ecm:currentLifeCycleState = 'Approved'
   AND ecm:isTrashed = 0
   AND ecm:isVersion = 0
   AND ecm:isProxy = 0
```

This call uses the **B**ulk **A**ction **F**ramework (see [here](https://doc.nuxeo.com/nxdoc/bulk-action-framework/)) and waits for completion, hence it should be called in an asynchronous process, not related to the UI at least.

##### Attaching a Dynamic Rule from the UI
*See below "User Interface" topic.*

##### Detaching a Rule

* JavaAPI from the `RetentionService` (`RetentionService#clearRule` and `RetentionService#clearRules`)
* Or Automation, then `Retention.RemoveRules` operation:
  * Input is the document a rule must be attached to
  * `ruleIds` is a list of rule IDs to be removed from the document
    * If not passed or empty, all rules are removed.


### Checking Rules to Start or End the Retention

- A post commit listener inspects all the documents with the `Record` facet for retention rules triggered by events
- A listener notified on `checkRetentionEvent` queries  for:
  1. Unmanaged Records with its `record:min_cutoff_at` lower than the current date (=> the retention       should start)
  2. Active Record with `record:max_retention_at` less or equal to the current date (the retention should end).

A scheduler is configured to trigger a `checkRetentionEvent` daily.

NOTE: This scheduler can be overridden to change the time at which it is triggered and/or the frequency


### Events Sent by the Plugin
Misc. events are triggered by the plugin:

* `retentionAboutToExpire`: Event triggered for documents whose `record:reminder_start_date` value is reached
* `retentionActive`: The document enters under active retention
* `retentionExpired`: The document exits active retention

It is then possible to listen to any of these events and trigger any logic that is required by the application.


### Enforcing that a Document Under Active Retention Cannot Be Modified.

This is implemented with a Security policy that denies write access to a document under retention active.


## User Interface
The plugin provides layouts, dialogs and widgets to handle retention.

### Overriding Default Slots
The plugin overrides default slots to make sure the UI does not display a button to modify/delete a document that is under retention: The delete and edit document buttons, and all the delete/replace blob buttons.

The buttons are hidden even if the current user is Administrator


### Deployment
All its UI elements are deployed at `{server}/nuxeo.war/ui/nuxeo-retention` and `{server}/nuxeo.war/ui/document/retentionconfiguration`.

#### `nuxeo-retention` folder

* `nuxeo-retention.html` is loaded at startup and just loads the other elements

* `retention-behavior.html` provides shared utilities

* `retention-action.html`:
  * A UI button that will display a nuxeo document suggestion to select a RetentionRule
  * It uses a page provider to fetch all documents with the `RetentionRule` facet
  * The behavior will display the button only if the document is not already under retention
  * Once a rule is selected it is immediately attached to the document, possibly starting the retention immediately depending on its configuration

* `retention-widget.html` displays a view letting the user knows the document is under retention, until when and what will happen once the retention ends
  * This element is not installed by default everywhere, you must explicitly use it in your layouts
  * (if the document is not under retention, nothing is displayed)
  * For example, to add it to a `File` document you could:
    * Configure the `nuxeo-file-view-layout.html` element
    * Just add `<retention-widget...>`:

```
<dom-module id="nuxeo-file-view-layout">

  <template>
    <style>
      nuxeo-document-viewer {
        @apply --paper-card;
      }
    </style>
    <!-- retention-widget is deployed by the nuxeo-retention plugin -->
    <retention-widget document="[[document]]"></retention-widget>
    <nuxeo-document-viewer role="widget" document="[[document]]"></nuxeo-document-viewer>
  </template>

  . . .
```

The `retention-widget.html` has some limitations:

* It only displays _first_ rule (in case there are a list of rules)
* Only _dynamic_ rule
* Only _end_ action(s)


#### `retentionconfiguration` folder

The ui/document/retentionconfiguration folder contains the different layouts that allows for creation, editing and displaying the  retentionConfiguration.

The `rule:beginActions` and `rule:endActions` are expected to store a list of operation to run. For convenience and better user experience, the plugin provides two vocabularies bound (in the UI) to these fields: `RetentionBegin` and `RetentionEnd`. It is possible to add values to these vocabularies, so you can add your own, custom operations to the list. See below "Overriding the actions vocabularies".

  > Important: Each operation only receives the current document as input, no parameters.


### Overriding the UI

To override the action, the widget, the layouts, ..., simply create in Studio the same hierarchy in Designer: a `nuxeo-retention` folder for example, with the element you want to override. To tune the action for example, duplicate the original (copy/paste the content) in your Studio and change the behavior.

#### Overriding the Actions Vocabularies

In the UI, the create and edit layouts for `RetentionConfiguration` document use vocabularies to improve the user experience when selecting a list of actions. It is possible to add your ow actions, and you probably want to to so since, by default, the plugin only suggests very few, like just `Document.Lock` or `Document.Unlock`.

The vocabulary IDs are `RetentionBegin` and `RetentionEnd`. To override them you can just create a vocabulary of the same name in Studio and set its creation policy to _always_. At startup or hot reload, this vocabulary will then replace the one created by the plugin.

<p>&nbsp;</p>

## Operations
#### `Retention.AttachRule`
Attach an existing retention rule to the input document.

* Input: The document to attach the rule to
* Output: The same document, modified and possibly sent to retention immediately
* Paramaters:
  * `ruleId`
    * Required
    * String, unique name of an XML contribution or the UUID of a document with the `RetentionRule` facet

#### `Retention.BulkAttachRule`
Attach an existing retention rule to the documents returned by an NXQL query.

The call uses the _Bulk Action Framework_ and waits for completion: It should be ran asynchronously.

* Input: `void`
* Output: `void`
* Paramaters:
  * `ruleId`
    * Required
    * String, unique name of an XML contribution or the UUID of a document with the `RetentionRule` facet
  * `nxql`
    * Required
    * The NXQL query to perform

#### `Retention.RemoveRules`
Remove a list of retention rules. If `ruleIds` is empty, removes all the rules and the `Record` facet.

* Input: The document to which we want to remove 1-n rules
* Output: The same document, after the rules were cleared
* Paramaters:
  * `ruleIds`
    * List of rule IDs (String, separated by commas). either unique names of XML contribution(s) or UUID(s) of document(s) with the `RetentionRule` facet
    * If empty or null all and every tules are removed and the `Record` facet is cleared


#### `Retention.BulkRemoveRule`
Clear an existing retention rule from the documents returned by the NXQL query. If `ruleId` is empty, the `Record` facet is removed (which removes all the rules).

The call uses the _Bulk Action Framework_ and waits for completion: It should be ran asynchronously.

* Input: `void`
* Output: `void`
* Paramaters:
  * `ruleId`
    * String, unique name of an XML contribution or the UUID of a document with the `RetentionRule` facet
    * If empty or null, the `Record` facet is removed (which removes all the rules)
  * `nxql`
    * Required
    * The NXQL query to perform


#### `Retention.CreateRule`
Creates a new retention rule to the input document. Adds the `RetentionRule` facet if needed. Returns a string, the UUID of the input document.

* Input: The document used for storing the rule
* Output: The input document, with the rule
* Paramaters:
  * NOTE:
    * Is is not possible to have both `beginAction` and `beginActions` set
    * Same for `endAction` and `endActions`
    * (An error is thrown if both parameters are set)
  * `beginDelayPeriod`
    * String. A Java period as string (i.e.: `"P1Y"`, `"P5Y6M3D"`)
    * Time to way before starting retention
    * If empty, retention starts as soon as the rule is attached to a document
  * `retentionPeriod`
    * String. A Java period as string (i.e.: `"P1Y"`, `"P5Y6M3D"`)
    * Duration of the retention
  * `beginAction`
    * String. The ID of an operation (or automation chain) to perform when the retention starts. The operation receives the document as input.
  * `beginActions`
    * A list of operations/automation chains to perform when the operation starts.
    * Called in the order of the list
    * Each receives the document as input and are called one after the other
  * `endAction`
    * String. The ID of an operation (or automation chain) to perform when the retention ends. The operation receives the document as input.
  * `endActions`
    * A list of operations/automation chains to perform when the operation ends.
    * Called in the order of the list
    * Each receives the document as input and are called one after the other
  * `beginCondExpression`
    * String
    * An `EL` expression, automatically evaluated in listeners. If the condition returns `true` the retention starts
  * `beginCondEvent`
    * a Core Event (documentModified, documentModified, ...). If this event is triggered, the plugin checks if the document should be put under retention
  * `endCondExpression`
    * An `EL` expression, automatically evaluated in listeners. If the condition returns `true` the retention starts

<p>&nbsp;</p>

## Tuning the Plugin
Besides overriding the UI and vocabularies, it is also possible to tune the settings when you plan to handle a lot of document sin a raw. The plugin uses theuses the **B**ulk **A**ction **F**ramework (see [here](https://doc.nuxeo.com/nxdoc/bulk-action-framework/)), which defines bucket sizes, batch sizes etc.

To override the behavior, just declare the same extension point as the one you can find in `retention-action-contrib.xml` and tune the properties. DO not forget to add the `<require>org.nuxeo.ecm.retention.actions<require>` tag to make sure your extension is called after the default one. 

<p>&nbsp;</p>

<hr>

## TODO

* Make it look better (UI) in the misc. layouts (create/edit/metadata of RetentionConfg, mainly)
* AllowChange the retention widget that displays only the first rule: If there are more than 1, display them all
* Allow UI to edit the rules on a document (not only the configuration)
 * use case: dynamically ad a  nw rule
 * Modify current rule because a Legal Hold must be put
 * . . .
* Allow for a mechanism where even an admin cannot modify a document under retention
  * A boolean, maybe, like "Admin can modify", default true
  * Security policy checks that and forbids modif by anyone when it's false
  * Even more tuned (maybe for a v2? ;-)) => list groups which _can_ modify a document under retention
* In all case, allow for legal hold
  * So a docuent is under retention and a new rule must be added because the document is used in a lawsuit
  * (this will be tricky if "admin cannot modifi)
* Add doc about retention-widget:
  * TBD in dev - Add and display only if relevant (info not empty):
    * Retention delay
    * Retention start
    * Retention start action
    * ... (in shot: more about the retention)
* In RetentionConfiguration, facets, types and states are not used to filter and handle the list of available rules => in "Apply Retention" dialog, filter available rules for the context (doc type, facet, lifecycle state)
* Add an admin dashboard with statistics
  * Number of doc under active retention/expired retention
  * Number which expire in the enxt 12 months
  * ...
* test adding more than a rule (usecase: "Legal Hold")
* Add more unit tests
  * Several rules applied one after the other
  * All possible condition (event, EL, ...)
    * Not _all_ possible :-), but covering every field used in the plugin

<hr>
<p>&nbsp;</p>

## Build and Deploy

#### Build

Assuming maven is installed:

```
cd /path/to/nuxeo-retention
mvn clean install
```

#### Deploy

The build creates the marketplace package at `nuxeo-retention/nuxeo-retention-package/target/nuxeo-retention-package-sandbox-{version}.zip`

Install this package, for example using the `nuxeoctl` command line:

```
#assuming you are in the bin directory, with the correct permissions
./nuxeoctl mp-install /path/to/nuxeo-retention-package-sandbox-10.10-SNAPSHOT.zip
```

<p>&nbsp;</p>


## Simple test:
Create a dynamic retention rule that puts the document in retention when the document is modified and attach it to a document

* Create the dynamic retention rule for 100 days triggered on 'documentModified'.
This rule is persisted as a facet on the input document. `"docId"` is the ID of the document used to store the retention rule.

```
POST /Retention.CreateRule
with:
{
  "docId": "65a47c93-5ac7-4ad1-ada8-f0c8201e3ae5",
  "params":{
	"retentionPeriod": "100D",
	"beginCondEvent" :"documentModified"
  }
}
```
=> this returns the id of the rule. In our example, say `65a47c93-5ac7-4ad1-ada8-f0c8201e3ae5`

* Attach the rule to the input document.
*
```
POST /Retention.AttachRule
{
  "input": "01d0b119-ef17-49ed-8ffd-fef7ba48ce42",
  "params":{
	"ruleId": "65a47c93-5ac7-4ad1-ada8-f0c8201e3ae5"
  }
}
```

The first time the document is modified, it will pass under retention active.

<p>&nbsp;</p>

## Resources (Documentation and other links)

## Contributing / Reporting issues

Link to JIRA component (or project if there is no component for that project).
Sample: https://jira.nuxeo.com/browse/NXP/component/14503/
Sample: https://jira.nuxeo.com/secure/CreateIssue!default.jspa?project=NXP

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Sample: https://github.com/nuxeo/nuxeo-drive

## About Nuxeo

[Nuxeo](www.nuxeo.com), developer of the leading Content Services Platform, is reinventing enterprise content management (ECM) and digital asset management (DAM). Nuxeo is fundamentally changing how people work with data and content to realize new value from digital information. Its cloud-native platform has been deployed by large enterprises, mid-sized businesses and government agencies worldwide. Customers like Verizon, Electronic Arts, ABN Amro, and the Department of Defense have used Nuxeo's technology to transform the way they do business. Founded in 2008, the company is based in New York with offices across the United States, Europe, and Asia.

Learn more at www.nuxeo.com.

