class EclipseScriptsGrailsPlugin {
    // the plugin version
    def version = "1.0.7.3"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2.0 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [ "grails-app/**" ]

    def author = "Lari Hotari"
    def authorEmail = "lari.hotari@sagire.fi"
    def title = "Eclipse settings generator"
    def description = '''\\
Creates .classpath and .project files for Eclipse/STS integration.
Downloads the sources and javadocs for dependencies from public repositories.
Links the sources and javadocs to the dependent libraries.

example use (Eclipse / Groovy-Eclipse plugin, no STS):
grails compile
grails download-sources-and-javadocs (run twice in a row)
grails download-sources-and-javadocs
grails create-eclipse-files
sh scripts/create_cpvardirs_unix.sh (unix) or scripts/create_cpvardirs_windows.bat (windows)
then import eclipse_workspace_settings.epf Eclipse Preference to your Eclipse Workspace

example use (STS Grails dependency manager compatible):
grails integrate-with --eclipse
grails compile
grails download-sources-and-javadocs (run twice in a row)
grails download-sources-and-javadocs
grails sts-link-sources-and-javadocs

For more information:
grails help download-sources-and-javadocs
grails help create-eclipse-files
grails help sts-link-sources-and-javadocs
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/eclipse-scripts"

    def license = "APACHE"
    def scm = [ url: "https://github.com/lhotari/grails-eclipse-scripts" ]
}
