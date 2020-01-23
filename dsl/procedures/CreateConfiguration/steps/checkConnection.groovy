$[/myProject/groovy/lib/GCPWrapper.groovy]

import com.electriccloud.client.groovy.ElectricFlow

// Sample code
ElectricFlow ef = new ElectricFlow()
String projectId = ef.getProperty(propertyName: 'projectId')?.property?.value
def credential = ef.getFullCredential(credentialName: 'credential')
def key = credential?.credential?.password
GCPWrapper gcp = new GCPWrapper(key, projectId)
gcp.listBuckets()