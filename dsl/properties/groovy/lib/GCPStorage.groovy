import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.cloudbees.flowpdf.*
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import org.codehaus.groovy.control.CompilerConfiguration

import java.util.regex.Pattern

import static org.slf4j.Logger.ROOT_LOGGER_NAME as ROOT
import static org.slf4j.LoggerFactory.getLogger


/**
 * GCPStorage
 */
class GCPStorage extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
            pluginName         : '@PLUGIN_KEY@',
            pluginVersion      : '@PLUGIN_VERSION@',
            configFields       : ['config'],
            configLocations    : ['ec_plugin_cfgs'],
            defaultConfigValues: [:]
        ]
    }

    @Lazy
    private GCPWrapper storage = {
        Config config = context.getConfigValues()
        String key = config.getCredential('credential')?.secretValue
        if (!key) {
            throw new RuntimeException("The key is not found in the credential")
        }
        GCPWrapper wrapper = new GCPWrapper(key)
        return wrapper
    }()


    def checkConnection(StepParameters p, StepResult sr) {
        try {
            storage.testConnection()
        } catch (Throwable e) {
            sr.setOutcomeProperty("/myJob/configError", e.message)
            throw e
        }
    }


    /**
     * downloadObjects - Download Objects/Download Objects
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param bucketName (required: true)
     * @param path (required: true)
     * @param dest (required: true)

     */
    def downloadObjects(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "downloadObjects was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String path = p.getRequiredParameter('path')?.value
        String dest = p.getRequiredParameter('dest')?.value
        String bucket = p.getRequiredParameter('bucketName')?.value

        File d = new File(dest)
        if (!d.isAbsolute()) {
            d = new File(System.getProperty('user.dir'), dest)
        }
        boolean overwrite = p.getParameter('overwrite')?.value == 'true'
        DownloadOptions o = DownloadOptions.builder().overwrite(overwrite).build()
        storage.downloadObjects(bucket, path, d, o)
    }


    /**
     * listObjects - List Objects/List Objects
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param bucketName (required: true)
     * @param path (required: false)
     * @param resultProperty (required: true)

     */
    def listObjects(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "listObjects was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String bucket = p.getRequiredParameter('bucketName').value
        String path = p.getParameter('path')?.value ?: ''
        String resultProperty = p.getParameter('resultProperty')?.value

        def objects = storage.listObjects(bucket, path)
        def result = [:]
        objects.each {
            String name = it.getName()
            result[name] = [
                size                   : it.getSize(),
                createdAt              : it.getTimeCreated().toString(),
                createdEpochMiliseconds: it.getTimeCreated().value
            ]
            log.info "Found object: $name, size: ${it.getSize()}"
            if (resultProperty) {
                FlowAPI.setFlowProperty("$resultProperty/objects/$name/size", it.getSize().toString())
            }
        }
        String json = JsonOutput.toJson(result)
        log.info "Objects: $json"
        if (resultProperty) {
            FlowAPI.setFlowProperty("$resultProperty/json", json)
        }
        sr.setOutputParameter('objects', json)
        sr.apply()
    }

    /**
     * uploadFolder - Upload Folder/Upload Folder
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param bucketName (required: true)
     * @param folder (required: true)
     * @param destination (required: true)
     * @param makePublic (required: false)

     */
    def uploadFolder(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "uploadFolder was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        File source = new File(p.asMap.folder as String)
        if (!source.isAbsolute()) {
            source = new File(System.getProperty("user.dir"), p.asMap.folder as String)
            log.info "Loading files from $source"
        }
        List<Pattern> includes
        List<Pattern> excludes
        if (p.asMap.includePatterns) {
            includes = p.asMap.includePatterns.toString().split(/\s*\n+\s*/).collect {
                Pattern.compile(it)
            }
        }
        if (p.asMap.excludePatterns) {
            excludes = p.asMap.excludePatterns.toString().split(/\s*\n+\s*/).collect {
                Pattern.compile(it)
            }
        }
        List<StorageObject> objects = storage.uploadFolder(p.asMap.bucketName as String,
            p.asMap.destination as String,
            source,
            UploadOptions
                .builder()
                .overwrite(checked(p.asMap.overwrite as String))
                .makePublic(checked(p.asMap.makePublic as String))
                .includes(includes)
                .excludes(excludes)
                .cacheControl(p.asMap.cacheControl)
                .build()
        )

        String json = new JsonBuilder(objects).toPrettyString()
        log.info "Objects: $json"
        sr.setOutputParameter('objects', json)
        sr.apply()
        log.info "Applied output parameter"

        String resultProperty = p.getParameter('resultProperty')?.value
        if (resultProperty) {
            FlowAPI.setFlowProperty("$resultProperty/json", json)
            objects.each {
                String name = it.getName()
                FlowAPI.setFlowProperty("$resultProperty/objects/$name/link", it.getMediaLink())
                FlowAPI.setFlowProperty("$resultProperty/objects/$name/size", it.getSize() as String)
                FlowAPI.setFlowProperty("$resultProperty/objects/$name/contentType", it.getContentType())
                log.info "Set property $resultProperty/objects/$name"
            }
        }
    }


    private static boolean checked(def value) {
        return value == "true"
    }


    /**
     * uploadObject - Upload Object/Upload Object
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param bucketName (required: true)
     * @param objectPath (required: false)
     * @param folder (required: false)
     * @param includePattern (required: )
     * @param excludePattern (required: )
     * @param destination (required: true)
     * @param makePublic (required: false)
     * @param overwrite (required: )
     * @param resultProperty (required: true)

     */
    def uploadObject(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "uploadObject was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        File file

        if (p.asMap.objectPath) {
            file = new File(p.asMap.objectPath)
            if (!file.isAbsolute()) {
                file = new File(System.getProperty("user.dir"), p.asMap.objectPath)
            }
            if (!file.exists()) {
                throw new RuntimeException("The file $file does not exist")
            }
        } else {
            String include = p.asMap.includePattern
            String exclude = p.asMap.excludePattern
            String folder = p.asMap.folder
            def names = new FileNameByRegexFinder().getFileNames(folder, include, exclude)
            if (names.size() != 1) {
                throw new RuntimeException("Found files: ${names}. There must be only one file.")
            }
            file = names.get(0)
        }

        def object = storage.uploadObject(p.asMap.bucketName as String,
            p.asMap.destination as String,
            file,
            UploadOptions
                .builder()
                .overwrite(checked(p.asMap.overwrite))
                .makePublic(checked(p.asMap.makePublic))
                .cacheControl(p.asMap.cacheControl)
                .build()

        )
        String json = new JsonBuilder(object).toPrettyString()
        log.info "Uploaded object: $json"
        sr.setOutputParameter('object', json)
        sr.setOutputParameter('objectLink', object.getMediaLink())
        sr.apply()

        String result = p.getParameter('resultProperty')?.value
        if (result) {
            FlowAPI.setFlowProperty("$result/json", json)
            FlowAPI.setFlowProperty("$result/link", object.getMediaLink())
        }
    }

    /**
     * runScript - Run Script/Run Script
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param script (required: true)

     */
    def runScript(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "runScript was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String script = p.getRequiredParameter('script').value
        def storageClient = storage.storage
        def compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.scriptBaseClass = DelegatingScript.class.name
        def shell = new GroovyShell(this.class.classLoader, new Binding([storage: storageClient]), compilerConfiguration)
        def gcpScript = new StorageScript(storageClient, storage.project, FlowAPI.ec, storage)
        Script s = shell.parse(script)
        s.setDelegate(gcpScript)
        def result = s.run()
        log.info "Script evaluation result: $result"
        if (result) {
            sr.setOutputParameter('output', JsonOutput.toJson(result))
            sr.setJobStepSummary(new JsonBuilder(result).toPrettyString())
        }

    }

    /**
     * downloadObject - Download Object/Download Object
     * Add your code into this method and it will be called when the step runs
     * @param config (required: true)
     * @param bucketName (required: true)
     * @param path - file Name for downloading (required: true)
     * @param dest (required: true)
     * @param overwrite (required: )

     */
    def downloadObject(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "downloadObject was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )
        String path = p.getRequiredParameter('path')?.value
        String dest = p.getRequiredParameter('dest')?.value
        String bucket = p.getRequiredParameter('bucketName')?.value

        File d = new File(dest)
        if (!d.isAbsolute()) {
            d = new File(System.getProperty('user.dir'), dest)
        }
        boolean overwrite = p.getParameter('overwrite')?.value == 'true'
        DownloadOptions o = DownloadOptions.builder().overwrite(overwrite).build()
        storage.downloadObject(bucket, path, d, o)
        /*
        Context context = getContext()

        // Setting job step summary to the config name
        sr.setJobStepSummary(p.getParameter('config').getValue() ?: 'null')

        sr.setReportUrl("Sample Report", 'https://cloudbees.com')
        sr.apply()
        log.info("step Download Object has been finished")
        */
    }

    // === step ends ===

}


class StorageScript {
    def storage
    def project
    def ef
    def wrapper

    StorageScript(storage, project, ef, wrapper) {
        this.storage = storage
        this.project = project
        this.ef = ef
        this.wrapper = wrapper
    }

}