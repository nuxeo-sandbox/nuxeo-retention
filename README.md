[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-retention_master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-retention_master/)

## Principles & Concepts

A document under active retention can not be modified or deleted. 

### Rules definition


Each rule is composed of :

 - id
 - a condition for retention start / cutoff
       - event
       - duration       
 - a cutoff action: *what we do after entering cutoff*
       - Automation 
 - a condition for retention end
      - event
      - duration   
 - a retention end action : *what we do after entering cutoff*
      - automation 
 - the retention duration ( a Java period)
 - a begin delay 
 - retention-reminder-days: 
  


### Record facet

When a retention rule is applied on the document we store the data into the  Record facet that is added to each document under Retention.
The facet holds:


   - associated rule(s)
   - retention start (target or actual): min_cutoff_at
   - retention end  (target or actual) : max_retention_at ( composed from all rules if more than one rules are applied to this document)
   - status flag  ('unmanaged', 'active', 'expired')



## How it works

### Defining Rule 

There are two ways to contribute retention rules:
- statically, by contributing to the "rules" extension point in the RetentionService
- dynamically via the facet 'RetentionRule': in this case, the id of the rule is the id of the document where the facet is added ( using as a base storage for the rule)

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

### Enforcing that a document under active retention can not be modified.

This is implemented with a new Security policy that denies access to a document under retention active.


## Build

    mvn clean install


## Simple test:
Create a dynamic retention rule that puts the document in retention when the document is modified and attach it to a document

1. Create the dynamic retention rule for 100 days triggered on 'documentModified'.
This rule is persisted as a facet on the input document.
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
=> this sends back the id of the rule.
```
2. Attach the rule on the input document.
```
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
