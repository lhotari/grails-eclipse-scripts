import grails.util.BuildSettings
import org.codehaus.groovy.grails.resolve.IvyDependencyManager
import org.codehaus.groovy.grails.resolve.EnhancedDefaultDependencyDescriptor
import java.io.File

includeTargets << grailsScript("_GrailsInit")
includeTargets << grailsScript("_GrailsArgParsing")

USAGE = """
	grails download-sources-and-javadocs [--use-transfer-listener] [--ivy-log-level=debug|info]	
	--use-transfer-listener	shows Ivy transfer progress (Grails 1.3+ only)
	--ivy-log-level=debug|info	sets ivy's log level
	--delete-jars				deletes downloaded jars from ~/.grails-eclipse-scripts/cache after copying
	
	Ivy dependencies are downloaded in to a separate directory so that the ivy xml files are downloaded
	from central repositories and not generated from local files provided with grails. This ensures that
	javadocs and source files can be downloaded.
"""

target(main:"Downloads all sources and javadocs for all dependencies") {
    depends(parseArguments)
    
    boolean grails12version = (grailsSettings.grailsVersion.startsWith('1.2.'))
    boolean grails14version = (grailsSettings.grailsVersion.startsWith('1.4.'))
    
    def dep
    if(argsMap.params) {
        dep = argsMap.params[0].toString()
    }
    else if(argsMap.group && argsMap.name && argsMap.version) {
        dep = argsMap
    }
    
    BuildSettings buildSettings = grailsSettings
    
    def defaultDepManager = buildSettings.dependencyManager
    
    def clonedDepManager = new IvyDependencyManager(defaultDepManager.applicationName,defaultDepManager.applicationVersion,defaultDepManager.buildSettings) //,defaultDepManager.metadata)
    def cacheDir = new File("${System.getProperty('user.home')}/.grails-eclipse-scripts/cache")
    cacheDir.mkdirs()
    clonedDepManager.ivySettings.defaultCache = cacheDir
    def realCacheDir = defaultDepManager.ivySettings.defaultCache
    def ivyLogLevel = (argsMap.'ivy-log-level')?:'debug'
    if(!grails12version && argsMap.'use-transfer-listener') {
    	// Grails 1.3
    	clonedDepManager.transferListener = defaultDepManager.transferListener
    	if(!argsMap.'ivy-log-level') {
    		ivyLogLevel = 'warn'
    	}
    }
    
    println "Copying *.jar from ${realCacheDir} to ${cacheDir}"
    copy(todir: cacheDir, verbose: true, overwrite: false) {
        fileset(dir: realCacheDir) {
            include(name: "**/.jar")
            include(name: "org.grails/**/*")
        }
    }    
    
    clonedDepManager.parseDependencies {
		log ivyLogLevel
    	repositories {
	    	mavenLocal()
	    	mavenCentral()
	    	ebr()
	        mavenRepo "http://snapshots.repository.codehaus.org"
	        mavenRepo "http://repository.codehaus.org"
	        mavenRepo "http://download.java.net/maven/2/"
	        mavenRepo "http://repository.jboss.com/maven2/"
	        mavenRepo "http://repository.springsource.com/maven/bundles/release"
	        mavenRepo "http://repository.springsource.com/maven/bundles/external"
	        mavenRepo "http://repository.springsource.com/maven/bundles/milestone"    	
	        if(argsMap.repository) {
	            mavenRepo argsMap.repository.toString()
	        }
	        grailsPlugins()
	    	grailsHome()
    	}
        if(dep) {
        		compile dep
        }
    }

    defaultDepManager.dependencyDescriptors.each { dd ->
	dd.addDependencyConfiguration('*','sources(*)')
	dd.addDependencyConfiguration('*','javadoc(*)')
	clonedDepManager.configureDependencyDescriptor(dd, dd.scope)
	if(!grails14version) {
		clonedDepManager.addDependency(dd.dependencyRevisionId)
	}
    }
    
    def report = clonedDepManager.resolveDependencies()
    
    // try downloading sources and javadocs for all transitive dependencies
    report.allArtifactsReports.each { downloadReport ->
    	def mrid = downloadReport.artifact.moduleRevisionId
    	def dd = new EnhancedDefaultDependencyDescriptor(mrid, false, false, 'runtime')
	dd.addDependencyConfiguration('*','sources(*)')
	dd.addDependencyConfiguration('*','javadoc(*)')
	if(!grails14version) {
		clonedDepManager.addDependency(mrid)
	}
	clonedDepManager.configureDependencyDescriptor(dd, dd.scope)
    }
    
    report = clonedDepManager.resolveDependencies()
    
    println "Copying *-javadoc.jar and *-sources.jar from ${cacheDir} to ${realCacheDir}"
    copy(todir: realCacheDir, preservelastmodified: true, verbose: true) {
        fileset(dir: cacheDir) {
            include(name: "**/*-javadoc.jar")
            include(name: "**/*-sources.jar")
        }
    }
    
    if(argsMap.'delete-jars') {
	    println "Deleting *.jar files from ${cacheDir}, cache only ivy-module xml files for next run."
	    delete {
	    	fileset(dir: cacheDir) {
	    		include(name: "**/*.jar")
	    	}
	    }
    }
    
    if(report.hasError()) {
        println """There were errors resolving dependencies."""
        exit 1
    }
}

setDefaultTarget("main")