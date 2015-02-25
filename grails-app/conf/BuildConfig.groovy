grails.project.target.level = 1.6
grails.project.source.level = 1.6
grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
  inherits("global") {
  }
  log "warn"
  repositories {
    mavenLocal()
    grailsCentral()
    mavenRepo "http://repo.grails.org/grails/core"
  }
  dependencies {
  }
  plugins {
    build ':release:3.0.1', ':rest-client-builder:1.0.3', {
      export = false
    }
  }
}
