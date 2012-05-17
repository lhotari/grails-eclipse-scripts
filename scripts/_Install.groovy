//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

println "Eclipse scripts installed."
println """example use (quick start):

	grails download-sources-and-javadocs (might require running twice in a row)
	grails create-eclipse-files

    sh scripts/create_cpvardirs_unix.sh (unix) or scripts/create_cpvardirs_windows.bat (windows)
	
    then import eclipse_workspace_settings.epf Eclipse Preference to your Eclipse Workspace   	
	    	
	For more information:
	grails help download-sources-and-javadocs
	grails help create-eclipse-files
"""