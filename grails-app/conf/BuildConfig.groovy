grails.project.target.level = 1.6
grails.project.source.level = 1.6
grails.project.dependency.resolution = {
    inherits("global") {
    }
    log "warn"
    repositories {
        grailsCentral()
    }
    dependencies {
    }
    plugins {
 	build ':release:2.2.1', ':rest-client-builder:1.0.3', {
      		export = false
   	}
    }
}
