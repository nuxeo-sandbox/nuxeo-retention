[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-retention-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-retention-master/)

## Principles & Concepts

A document under active retention can not be modified or deleted.

<hr>
TODO:

* Add doc about vocabularies (localization, and overriding)
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

<hr>

### Rules definition

A rule can be defined:

- Statically, via an XML contribution (see below)
- Or dynamically, using a document storing the values in the `retention_rule` schema (prefix `rule`)
  - Note: Adding the `RetentionRule` facet to a document automatically adds the schema

Each rule is composed of:

 - An id
     - The name of an XML contribution (see below)
     - Or the UUID of a document defining the rules:
       - Must have the `retention_rule` schema
       - Note: Adding the `RetentionRule` facet to a document adds this schema
 - A condition for retention start:
     - event: A string, like "documentCreated", "documentModified", ...)
     - condition: A string, and `EL` expression (i.e: `document.getType()=='File'`, or `document.getPropertyValue('record:min_cutoff_at').before(currentDate)`, ...)     - **WARNING**: This is EL. Not MVEL, not JavaScript, no FreeMarker, ... 
 - A _begin_ action: *what we do after entering cutoff*
     - Automation
 - A condition for retention end
     - event: A string, like "documentCreated", "documentModified", ...)
     - condition: A string, and `EL` expression (see above)
 - A `beginDelayPeriod`: Java period which i'm not sure what it's doing
 - The retention duration (a Java period)
 - A duration in days for the reminder before end of retention. An event will be triggered when _(end of retention - reminderDays)_ is reached.
 - A retention end action : *what we do after entering cutoff*
    - Automation 
  
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
- statically, by contributing to the "rules" extension point in the RetentionService
- dynamically via the facet 'RetentionRule': in this case, the id of the rule is the id of the document where the facet is added ( using as a base storage for the rule)

### Static configuration

These are the steps to contribute with new rules as a extension point:
- Access to Nuxeo Studio
- Browse to *CONFIGURATION > Advanced Settings > XML Extensions*
- Create a new *XML extension* called *RETENTION*
- Add your rules. In this example we will add 3 rules:
  * Retain *File* document types during 1 year. Initially locked and deleted at the end of the period. Reminder sent 3 days before the end of the period.
  * Retain *Picture* document types during 1 year. Initially locked and deleted at the end of the period. Reminder sent 3 days before the end of the period.
  * Retain *Video* document types during 1 year. Initially locked and deleted at the end of the period. Reminder sent 3 days before the end of the period.  

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
    <retention-duration>P1Y</retention-duration>
    <retention-reminder-days>3</retention-reminder-days>
    <begin-action>Document.Lock</begin-action>
    <end-action>Document.Delete</end-action>
    <end-condition expression=""></end-condition>
  </rule>
  
    <rule>
    <id>videoRetentionWithReminder</id>
    <begin-condition expression="document.getType()=='Video'"></begin-condition>
    <begin-delay></begin-delay>
    <retention-duration>P1Y</retention-duration>
    <retention-reminder-days>3</retention-reminder-days>
    <begin-action>Document.Lock</begin-action>
    <end-action>Document.Delete</end-action>
    <end-condition expression=""></end-condition>
  </rule>
  
</extension>
```

### Attach a Rule

The following APIs are exposed in the RetentionService:
- a method to attach a rule on a single document:     
void attachRule(String ruleId, DocumentModel doc);
- a method to attach a rule to a query result
void attachRule(String ruleId, String query, CoreSession session);



### Checking rules to start or end the retention

- A post commmit listener inspects all the documents with the 'Record' facet for retention rules triggered by events
- A listener notified on 'checkRetentionEvent' queries  for: 
          1) unmanaged Records with the record:min_cutoff_at < currentDate ( the retention       should start)
          2) active Record with record:max_retention_at <= currentDate ( the retention should end).
       A scheduler is configured to trigger a 'checkRetentionEvent' daily.


### Events Sent by the Plugin
Misc. events are triggered b-y the plugin:

* `retentionAboutToExpire`: Event triggered for documents whose `record:reminder_start_date` value is reached
* `retentionActive`: The document enters under active retention
* `retentionExpired`: The document exists active retention

It is then possible to listen to any of these events and triggers any logic that is required by the application.

### Enforcing that a document under active retention can not be modified.

This is implemented with a new Security policy that denies access to a document under retention active.


## Build

    mvn clean install


## Simple test:
Create a dynamic retention rule that puts the document in retention when the document is modified and attach it to a document

1. Create the dynamic retention rule for 100 days triggered on 'documentModified'.
This rule is persisted as a facet on the input document.
```js
POST /Retention.CreateRule
with:
{
"docId": "65a47c93-5ac7-4ad1-ada8-f0c8201e3ae5", 
"params":{
	"retentionPeriod": "100D",
	"beginCondEvent" :"documentModified"
     }
}
=> this sends back the id of the rule.
```
2. Attach the rule on the input document.
```js
POST /Retention.AttachRule
{
"input": "01d0b119-ef17-49ed-8ffd-fef7ba48ce42", 
"params":{
	"ruleId": "65a47c93-5ac7-4ad1-ada8-f0c8201e3ae5"
     }
}
```
When the document is modified the first time it will pass under retention active.



## Deploy (how to install build product)

Direct to MP package if any. Otherwise provide steps to deploy on Nuxeo Platform: << Copy the built artifacts into `$NUXEO_HOME/templates/custom/bundles/` and activate the `custom` template. >>.

# Resources (Documentation and other links)

# Contributing / Reporting issues

Link to JIRA component (or project if there is no component for that project).
Sample: https://jira.nuxeo.com/browse/NXP/component/14503/
Sample: https://jira.nuxeo.com/secure/CreateIssue!default.jspa?project=NXP

# License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Sample: https://github.com/nuxeo/nuxeo-drive

# About Nuxeo

The [Nuxeo Platform](http://www.nuxeo.com/products/content-management-platform/) is an open source customizable and extensible content management platform for building business applications. It provides the foundation for developing [document management](http://www.nuxeo.com/solutions/document-management/), [digital asset management](http://www.nuxeo.com/solutions/digital-asset-management/), [case management application](http://www.nuxeo.com/solutions/case-management/) and [knowledge management](http://www.nuxeo.com/solutions/advanced-knowledge-base/). You can easily add features using ready-to-use addons or by extending the platform using its extension point system.

The Nuxeo Platform is developed and supported by Nuxeo, with contributions from the community.

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content oriented applications. Combining a powerful application development environment with
SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris.
More information is available at [www.nuxeo.com](http://www.nuxeo.com).
