[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-retention-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-retention-master/)

## Principles & Concepts

This plugin allows for defining retention rule(s) and attach the rule(s) to any number of documents. A document under active retention can then not be modified or deleted.

Rules allow for setting:

* Conditions to start the retention, with or without delay
* Conditions to end the retention, with or without a reminder
* Actions to run when the retention starts and ends (typically, lock the document, or when the retention ends, delete it, ...)

The plugin works out of the box, and the rules can be defined:

* Dynamically, using the `RetentionCOnfiguraiton` document (or any custom document having the `RetentionRule` facet
* Statically, via XML configuration within Nuxeo Studio.

Once rules are defined, they can be attached to a single document or to a list of documents in a single action.

The plugin regularly (once/day by default) checks:

* For document that will enter retention
* For document that need to exit retention

For each of these event and each of the document concerned by them, an event is triggered allowing for taking action accordingly with the application business rules (notify, archive, ...)

Please, see at the end this READM the "TODO - Work in Progess" topic.



### Rules Definition

A rule can be defined:

- Statically, via an XML contribution (see below)
- Or dynamically, using a document storing the values in the `retention_rule` schema (prefix `rule`)
  - Note: Adding the `RetentionRule` facet to a document automatically adds the schema

Each rule is composed of:

 - An id
     - The unique name of an XML contribution (see below)
     - Or the UUID of a document defining the rules:
       - Must have the `retention_rule` schema
       - Note: Adding the `RetentionRule` facet to a document adds this schema
 - A condition for retention start:
     - event: A string, like `documentCreated`, `documentModified", ...)
     - condition: A string, and `EL` expression (i.e: `document.getType()=='File'`, or `document.getPropertyValue('record:min_cutoff_at').before(currentDate)`, ...)
     - **WARNING**: This is EL. Not MVEL, not JavaScript, no FreeMarker, ... 
 - A _begin_ action: *what we do after entering cutoff*
     - The ID (name) of and Automation Chain
     - Field: `rule:beginAction`
 - **Or** A list of predefined actions, each being an operation.
   - Field: `rule:beginActions` (plural)
   - The plugin provides the `RetentionBegin` vocabulary for this purpose
   - This vocabulary can be localized, and the ID of each item is the ID of the operation
   - NOTE: It is not possible to have both a single Automation chain _and_ a list of predefined operations
 - A condition for retention end
     - event: A string, like `documentMoved`
     - condition: A string, and `EL` expression (see above)
 - A `beginDelayPeriod`: Java period which i'm not sure what it's doing
 - The retention duration (a Java period)
 - A duration in days for the reminder before end of retention. An event will be triggered when _(end of retention - reminderDays)_ is reached.
 - A retention end action : *what we do after entering cutoff*
    - Automation
    - The ID (name) of and Automation Chain
    - Field: `rule:endAction`
 - **Or** A list of predefined actions, each being an operation.
   - Field: `rule:endActions` (plural)
   - The plugin provides the `RetentionEnd` vocabulary
   - This vocabulary can be localized, and the ID of each item is the ID of the operation
   - NOTE: It is not possible to have both a single Automation chain _and_ a list of predefined operations
  
### TODO: Explain Vocabularies and localization

### Record facet

When a retention rule is applied on the document we store the data into the `Record` facet that is added to each document under retention.
The facet holds the `record` schema:

   - Status of the retention (`record:status`: 'unmanaged', 'active', 'expired')
   - List of associated rule(s) (`record:rules`)
   - Retention start (target or actual): `record:min_cutoff_at`
   - Retention end  (target or actual) : `record:max_retention_at`
   - The`record:reminder_start_date` field: When this value is not null and is reached, the `retentionAboutToExpire` event is fired. Configuration can listen to this event and act accordingly ( mail notificaiton, ...)



## How it works

### Defining Rule 

There are two ways to contribute retention rules:

- **Statically**, by contributing to the "rules" extension point in the RetentionService
- **Dynamically** via the `RetentionRule` facet: In this case, the id of the rule is the id of the document holding the facet. The facet comes with the `retention_rule` schema (prefix `rule`), that holds the values for the rule (see "Rules Definition")

#### Static configuration

These are the steps to contribute with new rules as a extension point:

- Access to Nuxeo Studio
- Browse to *CONFIGURATION > Advanced Settings > XML Extensions*
- Create a new *XML extension* called *RETENTION* (or whatever name you want to use)
- Add your rules. In this example we will add 2 rules:
  * Retain *File* document types during 1 year. Initially locked and deleted at the end of the period. Reminder sent 3 days before the end of the period.
  * Retain *Picture* document types during 3 month. Initially lock and then unlock and trash (not delete) at the end of the period. Reminder sent one week before the end of the period.

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
Just create and fill a document which has the `RetentionRule`` facet. The plugin provides out of the box the `RetentionConfiguration` document type for this purpose, with its layouts for the UI, allowing for setting the different fields (begin action(s), end action(s), delays, duration, ...)

*See below "User Interface" topic.*

### Attach a Rule

As soon as a rule is attached to a document:

* The `Record` facet is added (if not already present)
* Evaluation of the rule is done immediately. So, for example, the document may immediately go to retention, because there is no delay specified in the rule.

#### Attach a Rule with Java API
The following APIs are exposed in the `RetentionService`:

- A method to attach a rule on a single document:     
`void attachRule(String ruleId, DocumentModel doc);`
- A method to attach a rule to a query result
void attachRule(String ruleId, String query, CoreSession session);`

#### Attach a Rule with Automation
Also, an operation is provided: `Retention.AttachRule`:

* Input is the document a rule must be attached to
* `ruleId` is a strong parameter, the ID of a rule to attache:
  * Either the if of static (XML) rule
  * Or the UID of a Document with the `RetentionRule` facet

#### Attaching a Dynamic Rule from the UI
*See below "User Interface" topic.*

#### Detaching a Rule

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


### Enforcing that a Document Under Active Retention Can Not Be Modified.

This is implemented with a Security policy that denies write access to a document under retention active.


## User Interface
The plugin provides layouts, dialogs and widgets to handle retention.


### Deployment
All its UI is deployed at `{server}/nuxeo.war/ui/nuxeo-retention` and {server}/nuxeo.war/ui/document/retentionconfiguration`.

#### The nuxeo-retention folder

* `retention-behavior.html`, provides shared utilities
* `retention-action.html`: A UI button that will display a nuxeo document suggestion to select a RetentionRule


## Build

    mvn clean install


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

<hr>
## TODO - Work in Progess or Paused


* Add doc about vocabularies (localization, and overriding)
* Add doc about UI element disabled when under retention, (delete/edit)
* Make it look better in the misc. layouts (create/edit/metadata of RetentionConfg, mainly)
* Add doc about operations
* Add doc about retention-widget:
  * How to use it
  * It only displays first rule (in case of list of rules)
  * Only _dynamic_ rule
  * Only _end_ actions
  * TBD in dev - Add and display only if relevant (info not empty):
    * Retention delay
    * Retention start
    * Retention start action
    * ... (in shot: more about the retention)
* In RetentionConfiguration, facets, types and states are not used to filter and handle the list of available rules => in "Apply Retention" dialog, filter available rules for the context (doc type, facet, lifecycle state)
* Add more unit tests

<hr>

## Build and Deploy

#### Build

Assuming maven is installed:

```
cd /path/to/nuxeo-retention
mvn clean install
```

#### Deploy

The build creates the marketplace package at `nuxeo-retention/nuxeo-retention-package/target/nuxeo-retention-package-{version}.zip`

Install this package, for example using the `nuxeoctl` command line:

```
#assuming you are in the bin directory, with the correct permissions
# Installing version 10.10-SNAPSHOT
./nuxeoctl mp-install /path/to/nuxeo-retention-package-10.10-SNAPSHIT.zip
```


## Resources (Documentation and other links)

## Contributing / Reporting issues

Link to JIRA component (or project if there is no component for that project).
Sample: https://jira.nuxeo.com/browse/NXP/component/14503/
Sample: https://jira.nuxeo.com/secure/CreateIssue!default.jspa?project=NXP

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Sample: https://github.com/nuxeo/nuxeo-drive

## About Nuxeo

The [Nuxeo Platform](http://www.nuxeo.com/products/content-management-platform/) is an open source customizable and extensible content management platform for building business applications. It provides the foundation for developing [document management](http://www.nuxeo.com/solutions/document-management/), [digital asset management](http://www.nuxeo.com/solutions/digital-asset-management/), [case management application](http://www.nuxeo.com/solutions/case-management/) and [knowledge management](http://www.nuxeo.com/solutions/advanced-knowledge-base/). You can easily add features using ready-to-use addons or by extending the platform using its extension point system.

The Nuxeo Platform is developed and supported by Nuxeo, with contributions from the community.

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with
SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris.
More information is available at [www.nuxeo.com](http://www.nuxeo.com).
