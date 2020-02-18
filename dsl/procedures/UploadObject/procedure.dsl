// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'Upload Object', description: '''''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'Upload Object', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/UploadObject/steps/UploadObject.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[/myJobStep/parent/steps/flowpdk-setup/flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'object',
        description: 'JSON representation of the uploaded object'

    formalOutputParameter 'objectLink',
        description: 'Media link of the uploaded object.'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: aab74b93314a9476b6966328fa75e01b ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}