$[/myProject/groovy/lib/GCPWrapper.groovy]

import com.electriccloud.client.groovy.ElectricFlow

// Sample code
ElectricFlow ef = new ElectricFlow()
def credential = ef.getFullCredential(credentialName: 'credential')
def key = credential?.credential?.password
GCPWrapper gcp = new GCPWrapper(key)
try {
    gcp.listBuckets()
} catch (Throwable e) {
    ef.setProperty(propertyName: '/myJob/configError', value: e.getMessage())
    throw e
}
