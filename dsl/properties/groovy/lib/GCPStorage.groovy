import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.cloudbees.flowpdf.*

import static org.slf4j.Logger.ROOT_LOGGER_NAME as ROOT
import static org.slf4j.LoggerFactory.getLogger


/**
* GCPStorage
*/
class GCPStorage extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
                pluginName     : '@PLUGIN_KEY@',
                pluginVersion  : '@PLUGIN_VERSION@',
                configFields   : ['config'],
                configLocations: ['ec_plugin_cfgs'],
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
        String projectId = config.getRequiredParameter('projectId').value
        GCPWrapper wrapper = new GCPWrapper(key, projectId)
        return wrapper
    }()


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
        storage.downloadObjects(bucket, path, d)
    }

// === step ends ===

}