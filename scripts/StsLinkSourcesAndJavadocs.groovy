import java.io.File;
import grails.util.BuildSettings
import grails.util.PluginBuildSettings

import java.io.File

import org.codehaus.groovy.grails.plugins.GrailsPluginUtils
import org.springframework.core.io.Resource

includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsArgParsing")

USAGE = """grails sts-link-sources-and-javadocs [--clean]
	
	Adds links to downloaded sources and javadocs when STS Grails Dependency Management is active.
	Modifies the .settings/org.grails.ide.eclipse.core.prefs file 
	
	Usage:
	grails compile
	grails download-sources-and-javadocs
	grails download-sources-and-javadocs
	grails sts-link-sources-and-javadocs
	
	Refresh workspace and restart STS to show changes.
	
	--clean - remove previous source and javadoc links from org.grails.ide.eclipse.core.prefs file
"""

def resolveSourceAndJavadoc(File jarfile, File repositoryDir, boolean useMaven) {
    File resolvedSource = null
    String resolvedJavadoc = null

    def jarfilebase = jarfile.name.replaceAll(/\.jar$/,'')
    def jarsourcename = "${jarfilebase}-sources.jar".toString()
    File sourcejar = new File(useMaven ? jarfile.parentFile : new File(jarfile.parentFile.parentFile, 'sources'), jarsourcename)
    if(sourcejar.exists()) {
        resolvedSource=sourcejar.absoluteFile
    } else if(!isChildOfFile(jarfile, repositoryDir)) {
        def parentOrgDir = new File(repositoryDir, jarfile.parentFile.parentFile?.parentFile?.name)
        def parentDepDir = new File(parentOrgDir, jarfile.parentFile.parentFile?.name)
        if(parentDepDir.exists()) {
            sourcejar = new File(new File(parentDepDir,useMaven ? jarfile.parentFile.name : 'sources'), jarsourcename)
            if(sourcejar.exists()) {
                resolvedSource=sourcejar.absoluteFile
            }
        }
    }

    def jarorganization = sourcejar?.parentFile?.parentFile?.parentFile?.name

    if(!resolvedSource && argsMap.grailsSource && isChildOfFile(jarfile, new File(grailsSettings.grailsHome, 'dist'))) {
        def matcher = jarfile.name =~ /^(grails-.*?)-\d.*$/
        if(matcher.matches()) {
            def moduleName=matcher[0][1]
            def moduleSource=new File(argsMap.grailsSource, [
                moduleName,
                'src',
                'main',
                'groovy'
            ].join(File.separator))
            if(moduleSource.exists()) {
                resolvedSource=moduleSource
                jarorganization='org.grails'
            }
        }
    }

    File javadocjar = new File(useMaven ? jarfile.parentFile : new File(sourcejar?.parentFile.parentFile, 'javadocs'), "${jarfilebase}-javadoc.jar")
    if(javadocjar.exists()) {
        resolvedJavadoc = "jar:${javadocjar.toURL()}!/"
    } else if (jarorganization=='org.grails') {
        resolvedJavadoc = new File(grailsSettings.grailsHome,'doc/api').absoluteFile.toURL().toExternalForm()
    }
    [
        resolvedSource,
        resolvedJavadoc
    ]
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

target('default': "Links sources and javadocs downloaded to .ivy2/cache to STS Grails Dependencies") { stsLinkSourcesAndJavadocs() }

target(stsLinkSourcesAndJavadocs: "Links sources and javadocs downloaded to .ivy2/cache to STS Grails Dependencies") {
    depends(parseArguments)
    BuildSettings buildSettings = grailsSettings
    PluginBuildSettings pluginSettings = GrailsPluginUtils.getPluginBuildSettings()
    File repositoryDir = null
    boolean useMaven = false
    if (buildSettings.dependencyManager.getClass().name == 'org.codehaus.groovy.grails.resolve.maven.aether.AetherDependencyManager') {
        buildSettings.dependencyManager.with {
            repositoryDir = new File(cacheDir ?: settings.localRepository ?: DEFAULT_CACHE).absoluteFile
        }
        useMaven = true
    } else {
        repositoryDir = buildSettings.dependencyManager.ivySettings.defaultCache.absoluteFile
    }
    if (!repositoryDir || !repositoryDir.exists()) {
        System.err.println("Repository directory " + repositoryDir + " doesn't exist.")
        System.exit(1)
    }

    Set<File> jarFiles = new LinkedHashSet<File>()
    def addFileClosure = { File f ->
        File absFile = f.absoluteFile
        jarFiles.add(absFile)
    }
    buildSettings.getTestDependencies().each(addFileClosure)
    buildSettings.getTestDependencies().each(addFileClosure)
    buildSettings.getProvidedDependencies().each(addFileClosure)
    pluginSettings.getPluginJarFiles().each { Resource r ->
        addFileClosure(r.file)
    }

    File stsGrailsPrefsFile = new File("${basedir}/.settings/org.grails.ide.eclipse.core.prefs")
    File legacyStsGrailsPrefsFile = new File("${basedir}/.settings/com.springsource.sts.grails.core.prefs")

    String prefFileLinePrefix = 'org.grails.ide.eclipse.core.'
    if(!stsGrailsPrefsFile.exists() && legacyStsGrailsPrefsFile.exists()) {
        stsGrailsPrefsFile = legacyStsGrailsPrefsFile
        prefFileLinePrefix = 'com.springsource.sts.grails.core.'
    }

    Properties stsGrailsPrefs=new Properties()
    if(stsGrailsPrefsFile.exists()) {
        stsGrailsPrefsFile.withInputStream { input ->
            stsGrailsPrefs.load(input)
        }
    } else {
        stsGrailsPrefs.setProperty('eclipse.preferences.version','1')
    }
    if(argsMap.'clean') {
        for(def i=stsGrailsPrefs.entrySet().iterator();i.hasNext();) {
            def e=i.next()
            if(e.key.startsWith("${prefFileLinePrefix}source.attachment-") || e.key.startsWith("${prefFileLinePrefix}javadoc.location-")) {
                i.remove()
            }
        }
    }

    int sourceCount=0
    int javadocCount=0
    jarFiles.each { File f ->
        def (resolvedSource, resolvedJavadoc) = resolveSourceAndJavadoc(f, repositoryDir, useMaven)
        if(resolvedSource) {
            sourceCount++
            stsGrailsPrefs.setProperty("${prefFileLinePrefix}source.attachment-${f.name}", resolvedSource.absolutePath)
        }
        if(resolvedJavadoc) {
            javadocCount++
            stsGrailsPrefs.setProperty("${prefFileLinePrefix}javadoc.location-${f.name}", resolvedJavadoc)
        }
    }

    stsGrailsPrefsFile.withOutputStream { out ->
        stsGrailsPrefs.store(out, "Updated by sts-link-sources-and-javadocs")
    }

    println "\n${stsGrailsPrefsFile} has been updated. Sources found for ${sourceCount} jar files and javadocs for ${javadocCount} jar files.\nRestart STS if it is running.\n"
}