import java.io.File;
import java.util.LinkedHashSet;

import java.io.File
import grails.util.PluginBuildSettings
import grails.util.BuildSettings
import grails.util.Metadata
import org.springframework.core.io.Resource
import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import java.util.concurrent.atomic.AtomicBoolean

includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsArgParsing")

def createdFilesDescriptions = [
'.classpath':'Eclipse .classpath file',
'.project':'Eclipse .project file',
'eclipse_workspace_settings.epf':'Eclipse Preference file for importing classpath and linked resources / path variables to the Eclipse workspace',
'scripts/create_cpvardirs_unix.sh':'Unix script to create symbolic links to classpath variable directories (required for Javadoc linking since it doesn\'t support classpath/path variables)',
'scripts/create_cpvardirs_windows.bat':'The same for windows, requires linkd.exe from Microsoft rktools. Only for NTFS partitions.',
'scripts/create_cpvardirs_windows_uninstall.bat':'Uninstalls NTFS junction points.'
]

USAGE = """grails create-eclipse-files [--user-settings-only] [--windows] [--unix] [--cpvars-dir=/some/other/dir]
	if --user-settings-only is specified, only user specific files are created.
	--windows - create windows .bat files (create_cpvardirs_windows.bat)
	--unix - create unix .sh script (create_cpvardirs_unix.sh)
	
	--cpvars-dir=/some/other/dir - use /some/other/dir as parent directory for symbolic links to ~/.ivy2/cache and other classpath variables (used for javadoc linking)  
	
	If you want to link sources and javadocs to the dependent libraries , call "grails download-sources-and-javadocs" before calling this script.
	
	List of created files:
"""
createdFilesDescriptions.each { file, desc ->
	USAGE += "${file.padRight(50)}$desc\n"
}


def isChildOfFile(File child, File parent) {
	 def currentFile = child
    while(currentFile != null) {
    	if(currentFile == parent) {
    		return true
    	}
        currentFile = currentFile.parentFile
    }
	return false
}

def absolutePathForFile(File file) {
	file.absolutePath.replace(File.pathSeparatorChar, '/' as char)
}

def replacePathVars(File file, Map replacements, AtomicBoolean varReplacementDone = new AtomicBoolean(false)) {
	def fileStr = absolutePathForFile(file)
	for(e in replacements.entrySet()) {
		def newFileStr = fileStr.replaceAll(~"^${e.key}/(.*)\$", "${(e.value)?(e.value+'/'):''}\$1")
		if(newFileStr != fileStr) {
			varReplacementDone.set(true)
			return newFileStr
		}
	}
	return fileStr
}

def resolveSrcFolderName(File srcdir) {
	 def currentFile = srcdir
	 def pathParts = []
	 while(currentFile != null) {
		 pathParts += currentFile.name
		 if(currentFile.name == 'src' || currentFile.name == 'grails-app') {
    		pathParts += currentFile.parentFile.name
    		break
		 }	
		 currentFile = currentFile.parentFile
     }
	 def srcFolderName = '.plugin-src-' + pathParts.reverse().join('-')
}

def addSourceAndJavaDoc(jarfile, replacements, writer) {
	def jarfilebase = jarfile.name.replaceAll(/\.jar$/,'')
	File sourcejar = new File(new File(jarfile.parentFile.parentFile, 'sources'), "${jarfilebase}-sources.jar")
	if(sourcejar.exists()) {
		writer << " sourcepath=\""
		def sourcepathCleaned = replacePathVars(sourcejar, replacements)
		if(sourcepathCleaned.startsWith('GRAILS_')) {
			writer << '/'
		}
		writer << sourcepathCleaned << "\""
	}
	def jarorganization = sourcejar?.parentFile?.parentFile?.parentFile?.name
	File javadocjar = new File(new File(jarfile.parentFile.parentFile, 'javadocs'), "${jarfilebase}-javadoc.jar")
	if(javadocjar.exists()) {
		File javadocjarToUse
		javadocjarToUse=new File(eclipseCpVarDir, replacePathVars(javadocjar, replacements))
		if(!javadocjarToUse.exists()) {
			javadocjarToUse=javadocjar
		}
		writer << '>\n'
		writer << """\t<attributes>
\t\t<attribute name="javadoc_location" value="jar:${javadocjarToUse.toURL()}!/"/>
\t</attributes>\n"""
		return true
	} else if (jarorganization=='org.grails') {
		writer << " sourcepath=\"/GRAILS_HOME/src/java\">\n"
		writer << """\t<attributes>
\t\t<attribute name="javadoc_location" value="${new File(grailsSettings.grailsHome,'doc/api').toURL().toExternalForm()}"/>
\t</attributes>\n"""
		return true
	}
	return false
}

def createUserSettingsFiles(replacements, eclipseCpVarDir) {
	def settingsFile = new File("${basedir}/eclipse_workspace_settings.epf")
	settingsFile.withWriter('UTF-8') { writer ->
		replacements.each { key, value ->
			if(value) {
				writer << "org.eclipse.jdt.core/org.eclipse.jdt.core.classpathVariable.${value}=${key}\n"
			}
		}
		replacements.each { key, value ->
			if(value) {
				writer << "org.eclipse.core.resources/pathvariable.${value}=${key}\n"
			}
		}
	}
	
	new File("${basedir}/scripts").mkdir()
	
	boolean winOs = (System.getProperty("os.name").toLowerCase() =~ /(?i)windows/)
	
	if(argsMap.windows || winOs) {
		def cpVarDirsForJavadocsScriptBat = new File("${basedir}/scripts/create_cpvardirs_windows.bat")
		def winLf = String.valueOf(['\r' as char,'\n' as char] as char[])
		cpVarDirsForJavadocsScriptBat.withWriter('UTF-8') { writer ->
			writer << "@echo off${winLf}"
			writer << "REM javadocs cannot use classpath variables in Eclipse. Provide workaround so that a team can share common .classpath and .project files.${winLf}"
			writer << "REM change the default location with grails create-eclipse-files --cpvars-dir=/some/other/dir${winLf}"
			writer << "REM Get linkd.exe from Microsoft rktools: http://www.microsoft.com/downloads/details.aspx?FamilyID=9D467A69-57FF-4AE7-96EE-B18C4790CFFD${winLf}"
			writer << "REM linkd.exe is only for NTFS partitions (creates NTFS junction points)"
			writer << "mkdir \"${eclipseCpVarDir.absolutePath}\"${winLf}"
			writer << "cd \"${eclipseCpVarDir.absolutePath}\"${winLf}"
			replacements.each { key, value ->
				if(value && value != 'GRAILS_HOME') {
					writer << "linkd \"${value}\" \"${key}\"${winLf}"
				}
			}
			writer << "echo Links installed to ${eclipseCpVarDir.absolutePath}. Delete these with linkd directory /d or delrp directory command.${winLf}"
			writer << "echo Run grails create-eclipse-files again to create common paths to javadocs in .classpath (team members can share .classpath files and they can be in versioned in the source code repository)${winLf}" 
		}	
		def cpVarDirsForJavadocsScriptUninstallBat = new File("${basedir}/scripts/create_cpvardirs_windows_uninstall.bat")
		cpVarDirsForJavadocsScriptUninstallBat.withWriter('UTF-8') { writer ->
			writer << "@echo off${winLf}"
			writer << "REM removes linked directories${winLf}"
			writer << "cd \"${eclipseCpVarDir.absolutePath}\"${winLf}"
			replacements.each { key, value ->
				if(value && value != 'GRAILS_HOME') {
					writer << "linkd \"${value}\" /d${winLf}"
				}
			}
		}
	} 
	
	if(argsMap.unix || !winOs){
		def cpVarDirsForJavadocsScript = new File("${basedir}/scripts/create_cpvardirs_unix.sh")
		cpVarDirsForJavadocsScript.withWriter('UTF-8') { writer ->
			writer << "#!/bin/sh\n"
			writer << "# javadocs cannot use classpath variables in Eclipse. Provide workaround so that a team can share common .classpath and .project files.\n"
			writer << "# change the default location with grails create-eclipse-files --cpvars-dir=/some/other/dir\n"
			writer << "mkdir -p \"${eclipseCpVarDir.absolutePath}\"\n"
			writer << "cd \"${eclipseCpVarDir.absolutePath}\"\n"
			replacements.each { key, value ->
				if(value && value != 'GRAILS_HOME') {
					writer << "ln -s \"${key}\" \"${value}\"\n"
				}
			}
			writer << "echo \"Run grails create-eclipse-files again to create common paths to javadocs in .classpath (team members can share .classpath files and they can be in versioned in the source code repository)\"\n"
		}
	}

	println "\nImport ${settingsFile} to Eclipse with File->Import->General->Preferences. It contains classpath and path variables used for jar files, sources and javadocs.\n"
}

def createProjectFile(sourceFoldersCleaned, inlinePluginNames) {
	def projectFile = new File("${basedir}/.project")
	if(projectFile.exists()) {
		projectFile.renameTo(new File(projectFile.absolutePath + '.bak'))
	}
	
	projectFile.withWriter('UTF-8') { writer ->
		writer << """<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
        <name>${Metadata.current.getApplicationName()}</name>
        <comment></comment>
        <projects>
"""
		inlinePluginNames.each { pluginname ->
			writer << """			<project>${pluginname}</project>\n"""
		}
		writer << """        </projects>
        <buildSpec>
                <buildCommand>
                        <name>org.eclipse.jdt.core.javabuilder</name>
                        <arguments>
                        </arguments>
                </buildCommand>
        </buildSpec>
        <natures>
            <nature>com.springsource.sts.grails.core.nature</nature>
                <nature>org.eclipse.jdt.groovy.core.groovyNature</nature>
                <nature>org.eclipse.jdt.core.javanature</nature>
        </natures>
"""
		writer << "        <linkedResources>\n"
		sourceFoldersCleaned.each { sf ->
			writer << """	        <link>
	                <name>${sf.name}</name>
	                <type>2</type>
	                <location${(sf.varReplacementDone)?'URI':''}>${sf.path}</location${(sf.varReplacementDone)?'URI':''}>
	        </link>
"""
		}
		writer << "        </linkedResources>\n"
		
		writer << "</projectDescription>"
	}
}

def createClasspathFile(sourceFoldersCleaned, jarFilesCleaned, inlinePluginNames, replacements) {
	File classPathFile = new File("${basedir}/.classpath")
	if(classPathFile.exists()) {
		classPathFile.renameTo(new File(classPathFile.absolutePath + '.bak'))
	}
	classPathFile.withWriter('UTF-8') { writer ->
		writer << """<?xml version="1.0" encoding="UTF-8"?>	
<classpath>
<classpathentry kind="src" path="src/java"/>
<classpathentry kind="src" path="src/groovy"/>
<classpathentry kind="src" path="grails-app/conf"/>
<classpathentry kind="src" path="grails-app/controllers"/>
<classpathentry kind="src" path="grails-app/domain"/>
<classpathentry kind="src" path="grails-app/services"/>
<classpathentry kind="src" path="grails-app/taglib"/>
<classpathentry kind="src" path="test/integration"/>
<classpathentry kind="src" path="test/unit"/>
"""
		sourceFoldersCleaned.each { sf ->
			writer << "<classpathentry kind=\"src\" path=\"${sf.name}\"/>\n"
		}
		
		writer << """<classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
"""
		
		jarFilesCleaned.each { jarfile ->
			def kind = (jarfile.path.startsWith('GRAILS_'))?'var':'lib'
			writer << "<classpathentry kind=\"${kind}\" path=\"${jarfile.path}\""
			if(addSourceAndJavaDoc(jarfile.file, replacements, writer)) {
				writer << "</classpathentry>\n"
			} else {
				writer << "/>\n"
			}
		}	
		
		inlinePluginNames.each { pluginname ->
			writer << """<classpathentry combineaccessrules="false" kind="src" path="/${pluginname}"/>\n"""
		}
		
		writer << """<classpathentry kind="output" path="web-app/WEB-INF/classes"/>
</classpath>
"""
	}
}

target('default': "Generates .classpath and .project files for Eclipse") {
	createEclipseFiles()
}

target(createEclipseFiles: "Generates .classpath and .project files for Eclipse") {
	depends(parseArguments)
	
	BuildSettings buildSettings = grailsSettings
	PluginBuildSettings pluginSettings = GrailsPluginUtils.getPluginBuildSettings()
	File grailsWorkDir = buildSettings?.grailsWorkDir?.absoluteFile
	def eclipseCpVarDirProperty = argsMap.'cpvars-dir'
	if(eclipseCpVarDirProperty) {
		eclipseCpVarDir = new File(eclipseCpVarDirProperty)
	} else {
		eclipseCpVarDir = new File(buildSettings.grailsHome, 'eclipse-cpvars')
	}
	
	Set<File> jarFiles = new LinkedHashSet<File>()
	Set<File> sourceFolders = new LinkedHashSet<File>()
	
	Set<File> inlinePluginDirs = new LinkedHashSet<File>()
	pluginSettings.getInlinePluginDirectories().each { inlinePluginDirs += it.file.absoluteFile }
	Set<File> pluginDirs = new LinkedHashSet<File>()
	pluginSettings.getPluginDirectories().each { 
		def pluginDir = it.file.absoluteFile
		if(!inlinePluginDirs.contains(pluginDir)) {
			pluginDirs.add(pluginDir)
		}
	}
	
	jarFiles.addAll(buildSettings.getTestDependencies());
	jarFiles.addAll(buildSettings.getProvidedDependencies());

	for (Resource resource : pluginSettings.getPluginJarFiles()) {
		def jarFile = resource.file.absoluteFile
		def inlinePluginJarFile = false
		for(inlinePluginDir in inlinePluginDirs) {
			if(isChildOfFile(jarFile, inlinePluginDir)) {
				inlinePluginJarFile=true
				break
			}
		}
		if(!inlinePluginJarFile) {
			jarFiles.add(resource.file.absoluteFile);
		}
	}
	
	pluginDirs.each { pluginDir ->
		pluginSettings.getPluginSourceFiles(pluginDir).each { resource ->
			if(resource.file.exists() && resource.file.name != '.svn') {
				sourceFolders.add(resource.file.absoluteFile)
			}
		}
	}
	
	def ivyCacheDir = buildSettings.dependencyManager.ivySettings.defaultCache.absoluteFile
	
	def replacements = [:]
	replacements.put(absolutePathForFile(ivyCacheDir), 'GRAILS_IVYCACHE')
	replacements.put(absolutePathForFile(grailsWorkDir), 'GRAILS_WORKDIR')
	replacements.put(absolutePathForFile(buildSettings.grailsHome), 'GRAILS_HOME')
	replacements.put(absolutePathForFile(new File(basedir)), '')

	createUserSettingsFiles(replacements, eclipseCpVarDir)

	if(argsMap.'user-settings-only') {
		println "Creating only files for user's settings, exiting."
		return
	}
	
	def jarFilesCleaned = jarFiles.collect { [file: it, path: replacePathVars(it, replacements)] }
	def sourceFoldersCleaned = sourceFolders.collect { if(it.isDirectory()) { 
								AtomicBoolean varReplacementDone=new AtomicBoolean(false) 	
								def map=[name: resolveSrcFolderName(it), path:replacePathVars(it, replacements, varReplacementDone)]
								map.varReplacementDone = varReplacementDone.get()
								map
							} else { null } }.findAll{ it }
	def inlinePluginNames = inlinePluginDirs.collect { it.name }
	
	/*
	println "Jarfiles:" + jarFilesCleaned
	println "Source folders:" + sourceFoldersCleaned
	println "In-place plugins:" + inlinePluginNames
	println "Ivy cache dir:" +ivyCacheDir
	println "grailsWorkDir:" + grailsWorkDir
	*/
	
	createProjectFile(sourceFoldersCleaned, inlinePluginNames)

	createClasspathFile(sourceFoldersCleaned, jarFilesCleaned, inlinePluginNames, replacements)
}