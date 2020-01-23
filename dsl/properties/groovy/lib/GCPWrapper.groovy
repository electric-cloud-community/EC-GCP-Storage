import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.ObjectAccessControl
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.storage.StorageScopes

import groovy.util.logging.Slf4j

@Slf4j
class GCPWrapper {

    Storage storage
    String project

    GCPWrapper(String key, String project) {
        GoogleCredential credential = GoogleCredential.fromStream(new ByteArrayInputStream(key.getBytes('UTF-8')))

        List<String> scopes = new ArrayList<>()
        // Set Google Cloud Storage scope to Full Control.
        scopes.add(StorageScopes.DEVSTORAGE_FULL_CONTROL)
        credential = credential.createScoped(scopes)

        //https://github.com/GoogleCloudPlatform/java-docs-samples/blob/master/compute/cmdline/src/main/java/ComputeEngineSample.java
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        storage = new Storage.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName('@PLUGIN_NAME@')
            .build()
        this.project = project
    }

    def downloadObject() {

    }

    def downloadObjects(String bucket, String path, File dest) {
        dest.mkdirs()
        log.info "Created $dest.absolutePath"
        List<StorageObject> objects = storage.objects().list(bucket).execute().getItems()
        for (StorageObject so in objects) {
            if (so.getName().startsWith(path) && !so.getName().endsWith('/')) {
                log.info "Found object ${so.getName()}"
                String relPath = so.getName().replaceAll(path, '')
                if (relPath) {
                    File file = new File(dest, relPath)
                    log.info "Downloading file $file.absolutePath"
                    file.parentFile.mkdirs()
                    InputStream is = storage.objects().get(bucket, so.getName()).executeMediaAsInputStream()
                    file.withOutputStream { os ->
                        os << is
                    }
                    log.info "Downloaded file $file.absolutePath"
                }
            }
        }
    }

    void listBuckets() {
        storage.buckets().list(project).execute().getItems().each {
            log.info "Bucket ${it.getName()}"
        }
    }


    void uploadObject(String bucket, String path, File file) {
        //TODO visibility
        String contentType = file.toURL().openConnection().contentType
        InputStreamContent contentStream = new InputStreamContent(contentType,
            new FileInputStream(file))
        contentStream.setLength(file.length())
        StorageObject objectMetadata = new StorageObject().setName(path)
        storage.objects().insert(bucket, objectMetadata, contentStream).execute()


        /*
            InputStreamContent contentStream = new InputStreamContent(
        contentType, new FileInputStream(file));
    // Setting the length improves upload performance
    contentStream.setLength(file.length());
    StorageObject objectMetadata = new StorageObject()
        // Set the destination object name
        .setName(name)
        // Set the access control list to publicly read-only
        .setAcl(Arrays.asList(
            new ObjectAccessControl().setEntity("allUsers").setRole("READER")));

    // Do the insert
    Storage client = StorageFactory.getService();
    Storage.Objects.Insert insertRequest = client.objects().insert(
        bucketName, objectMetadata, contentStream);

    insertRequest.execute();
         */

    }
}
