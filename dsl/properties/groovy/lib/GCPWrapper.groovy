import com.cloudbees.flowpdf.Credential
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
import groovy.io.FileType
import groovy.json.JsonSlurper
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import org.apache.tika.Tika

import java.nio.file.Path
import java.time.DateTimeException
import java.util.regex.Pattern

@Slf4j
class GCPWrapper {

    Storage storage
    String project

    private GoogleCredential credential

    GCPWrapper(String key) {
        credential = GoogleCredential.fromStream(new ByteArrayInputStream(key.getBytes('UTF-8')))

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

        String projectId = credential.getServiceAccountProjectId()
        log.info "Using project $projectId"
        this.project = projectId
    }

    def testConnection() {
        storage.projects().serviceAccount().get(project).execute()
        return true
    }

    def downloadObject(String bucket, String fileName, File dest, DownloadOptions o) {
        log.info "Created $dest.absolutePath"
        log.info "Downloading File $fileName from Bucket: $bucket, to folder: $dest.absolutePath"
        dest.parentFile.mkdirs()
        if (dest.exists() && !o.overwrite) {
            throw new RuntimeException("The file $dest.absolutePath already exists")
        }
        InputStream is = storage.objects().get(bucket, fileName).executeMediaAsInputStream()
        dest.withOutputStream { os ->
            os << is
        }
        log.info "The File $fileName from Bucket: $bucket, to folder: $dest.absolutePath has been downloaded successfully"
    }

    def downloadObjects(String bucket, String path, File dest, DownloadOptions o) {
        dest.mkdirs()
        log.info "Created $dest.absolutePath"
        List<StorageObject> objects = listObjects(bucket, path)
        for (StorageObject so in objects) {
            log.info "Found object: ${so.getName()}"
            if (!so.getName().endsWith('/')) {
                log.info "Found object ${so.getName()}"
                String relPath = so.getName().replaceAll(path, '')
                if (relPath) {
                    File file = new File(dest, relPath)
                    log.info "Downloading file $file.absolutePath"
                    file.parentFile.mkdirs()
                    if (file.exists() && !o.overwrite) {
                        throw new RuntimeException("The file $file.absolutePath already exists")
                    }
                    InputStream is = storage.objects().get(bucket, so.getName()).executeMediaAsInputStream()
                    file.withOutputStream { os ->
                        os << is
                    }
                    log.info "Downloaded file $file.absolutePath"
                }
            }
        }
    }

    List<StorageObject> listObjects(String bucket, String path) {
        path = path.replaceAll(/\/$/, '')
        def operation = storage.objects().list(bucket).setPrefix(path).execute()
        String nextPageToken = operation.getNextPageToken()
        log.info "Looking for objects with names starting with $path"
        log.info "Next page token: $nextPageToken"
        List<StorageObject> retval = []
        List<StorageObject> objects = operation.getItems()
        log.info "Found ${objects.size()} objects"
        retval.addAll(objects)
        while (nextPageToken) {
            operation = storage.objects().list(bucket).setPrefix(path).setPageToken(nextPageToken).execute()
            nextPageToken = operation.getNextPageToken()
            log.info "Next page token: $nextPageToken"
            log.info "Found ${operation.getItems().size()} objects"
            retval.addAll(operation.getItems())
        }
        log.info "Found objects total: ${retval.size()}"
        return retval
    }

    void listBuckets() {
        log.info "Listing buckets from project $project"
        storage.buckets().list(project).execute().getItems().each {
            log.info "Bucket ${it.getName()}"
        }
    }

    List<StorageObject> uploadFolder(String bucket, String path, File folder, UploadOptions o) {
        List<StorageObject> result = []

        folder.eachFileRecurse(FileType.FILES) { file ->
            log.info "Found file $file.absolutePath"
            Path relative = folder.toPath().relativize(file.toPath())
            String rel = relative.toString().replaceAll('\\\\', '/')
            if (o.includes) {
                Pattern match = o.includes.find { rel =~ it }
                if (!match) {
                    log.info "$rel does not includes regular expressions"
                    return
                }
            }
            if (o.excludes) {
                Pattern match = o.excludes.find { rel =~ it }
                if (match) {
                    log.info "$rel matches one of the excludes regular expressions: ${match.toString()}, skipping"
                    return
                }
            }
            String bucketPath = "$path/$rel"
            log.info "Uploading as $bucketPath"
            StorageObject object = uploadObject(bucket, bucketPath, file, o)
            result << object
        }
        return result
    }


    StorageObject uploadObject(String bucket, String path, File file, UploadOptions p) {
        Tika tika = new Tika()
        String contentType = tika.detect(file)
        log.info "File $file has content type $contentType"
        InputStreamContent contentStream = new InputStreamContent(contentType, new FileInputStream(file))
        contentStream.setLength(file.length())
        log.info "Set length to ${file.length()}"
        StorageObject objectMetadata = new StorageObject().setName(path)
        if (p.makePublic) {
            //Public access
            objectMetadata.setAcl(Arrays.asList(new ObjectAccessControl().setEntity("allUsers").setRole("READER")))
        }
        if (p.cacheControl) {
            objectMetadata.setCacheControl(p.cacheControl)
        }
        log.debug "Metadata: $objectMetadata"
        log.info "Set name to $path"
        StorageObject existing
        try {
            existing = storage.objects().get(bucket, path).execute()
            log.info "Found existing object ${existing.getSelfLink()}"
        }
        catch (Throwable ignore) {
        }
        if (existing && !p.overwrite) {
            throw new RuntimeException("The object ${existing.getMediaLink()} already exists and overwrite flag is not set")
        }

        StorageObject object = storage.objects().insert(bucket, objectMetadata, contentStream).execute()
        log.info "Uploaded object $path to ${object.getMediaLink()}"
        return object
    }


    void deleteObject(String bucket, String path, boolean failIfMissing) {
        boolean exist = false
        try {
            storage.objects().get(bucket, path).execute()
            exists = true
        }
        catch (Throwable e) {
            //todo correct exception
            if (failIfMissing) {
                throw e
            }
            else {
                log.info "Failed to get object $path"
                return
            }
        }
        storage.objects().delete(bucket, path).execute()
        log.info "Object $path has been deleted"
    }

    void deleteFolder(String bucket, String path) {
        List<StorageObject> objects = storage.objects().list(bucket).setPrefix(path).execute().getItems()
        objects.each {
            log.info "Going to delete object ${it.getName()}"
            storage.objects().delete(bucket, it.getName()).execute()
            log.info "Deleted ${it.getName()}"
        }
    }

    void test() {
        storage.objects().list('flow-plugin-team-test-harness').execute().getItems().each {
            it.getUpdated().value
        }
    }
}

@Builder
class DownloadOptions {
    boolean overwrite
}

@Builder
class UploadOptions {
    boolean overwrite
    boolean makePublic
    List<Pattern> includes
    List<Pattern> excludes
    String cacheControl
}