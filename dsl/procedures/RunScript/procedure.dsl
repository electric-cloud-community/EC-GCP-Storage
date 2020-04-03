// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'Run Script', description: '''Runs a custom groovy script with the prepared Storage client''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'Run Script', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/RunScript/steps/RunScript.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'output',
        description: 'Some output parameter to use in the script'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: bd1c1c93206eaebc894f3318d53db723 ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}