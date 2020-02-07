// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'List Objects', description: '''This procedure lists the objects in the specified bucket''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'List Objects', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/ListObjects/steps/ListObjects.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'objects',
        description: 'JSON list of objects found'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: 62dce8ed036a8cbc983c9079b9b8f5cf ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}