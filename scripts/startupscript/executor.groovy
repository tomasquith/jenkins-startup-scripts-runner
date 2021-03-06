import com.buildit.jenkins.configfetcher.ConfigFetcher
import com.buildit.artifactfetcher.ArtifactFetcher
import org.codehaus.groovy.control.CompilerConfiguration

import static groovy.io.FileType.FILES

def main(){

    def jenkinsConfig = new ConfigFetcher().fetch()

    def jenkinsStartupScripts = System.getenv("JENKINS_STARTUP_SCRIPTS")

    if(jenkinsStartupScripts){
        def baseDirectory = baseDirectory()
        def classLoader = getAugmentedClassLoader("${baseDirectory}/lib")
        def bits = jenkinsStartupScripts.split(",")
        bits.each {
            runMain(it, [jenkinsConfig: jenkinsConfig, baseDirectory: jenkinsStartupScripts], classLoader)
        }
    }else{
        jenkinsConfig.startupScripts.each { key, value ->
            def artifacts = new ArtifactFetcher().fetch(value.artifactPattern, value.artifacts)
            artifacts.each { artifact ->
                def temp = createTempDirectory()
                unzip((artifact as File).absolutePath, temp)
                def scriptsClassLoader = getAugmentedClassLoader("${temp.absolutePath}/lib")
                runMain(temp.absolutePath, [jenkinsConfig: jenkinsConfig, baseDirectory: temp.absolutePath], scriptsClassLoader)
            }
        }
    }
}

def baseDirectory(){
    File thisScript = new File(getClass().protectionDomain.codeSource.location.path)
    return thisScript.getParent()
}

def unzip(zip, dir) {
    new AntBuilder().unzip(src: zip, dest:dir)
}

private void runMain(baseDirectory, args, classLoader) {
    def main = load("${baseDirectory}/main.groovy", args, classLoader)
    main.main()
}

def load(script, args=[:], classLoader=this.class.classLoader){
    CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
    def shell = new GroovyShell(classLoader, new Binding(), compilerConfiguration)
    def file = new File("${script}")
    try{
        if(file.exists()){
            println("Loading script : ${file.absolutePath}")
            shell.getClassLoader().addURL(file.parentFile.toURI().toURL())
            def result = shell.evaluate("new " + file.name.split("\\.", 2)[0] + "()")
            args.each{ k, v -> result."${k}" = v}
            return result
        }
    }catch(Exception e){
        println("Error loading script : ${e.getMessage()}")
        e.printStackTrace()
    }
}

def getAugmentedClassLoader(String libDirectory){
    def additionalJars = []
    def lib = new File(libDirectory)
    if(lib.exists()){
        lib.traverse(type: FILES, maxDepth: 0) {
            additionalJars.add(it.toURI().toURL())
        }
    }
    return new URLClassLoader(additionalJars as URL[], this.class.classLoader)
}

private File createTempDirectory() {
    File dir = new File("${System.getProperty("java.io.tmpdir")}/${UUID.randomUUID().toString()}")
    if(!dir.exists()) dir.mkdirs()
    return dir
}