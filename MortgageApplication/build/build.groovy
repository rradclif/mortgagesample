import com.ibm.team.dbb.build.*
import com.ibm.team.dbb.build.report.*
import com.ibm.team.dbb.build.html.*
import com.ibm.team.dbb.repository.*
import com.ibm.team.dbb.dependency.*
import groovy.time.*

//*
// Initialize Build
//*
def startTime = new Date()
println("** Build start at $startTime")

// load optional command line arguments
def propDir = System.getProperty("propDir")
def workDir = System.getProperty("workDir")
def buildFile = System.getProperty("buildFile")
def buildList = System.getProperty("buildList")
def logEncoding = System.getProperty("logEnc")

// load property files
def properties = BuildProperties.getInstance()

// overriding the property directory allows startup property customization
if (!propDir) { propDir = "." }

// startup.properties contains user specific (sandbox) properties
properties.load(new File("$propDir/startup.properties"))
// datasets.properties contains system specific PDS names used by Mortgage Application build
properties.load(new File("$properties.sourceDir/MortgageApplication/build/datasets.properties"))
// file.properties contains file specific properties like script mappings and CICS/DB2 content flags
properties.load(new File("$properties.sourceDir/MortgageApplication/build/file.properties"))

// override log encoding from command line argument (this allows Jenkins to set it to UTF-8)
if (logEncoding) { properties.logEncoding = logEncoding }

// create unique build directory in work directory
if (workDir) { properties.workDir = workDir }
def ts = startTime.format("yyyyMMdd.hhmm")
properties.buildDir = "$properties.workDir/build.$ts" as String
new File(properties.buildDir).mkdirs()
println("** Build output will be in $properties.buildDir")

// create datasets if necessary
def srcOptions = "cyl space(1,1) lrecl(80) dsorg(PO) recfm(F,B) dsntype(library) msg(1)"
def loadOptions = "cyl space(1,1) dsorg(PO) recfm(U) blksize(32760) dsntype(library) msg(1)" 
def srcDatasets = ["COBOL", "COPYBOOK", "OBJ", "BMS", "DBRM", "LINK", "MFS"]
def loadDatasets = ["LOAD", "TFORMAT"]
srcDatasets.each { dataset ->
	new CreatePDS().dataset("${properties.hlq}.$dataset").options(srcOptions).create()
}

loadDatasets.each { dataset ->
	new CreatePDS().dataset("${properties.hlq}.$dataset").options(loadOptions).create()
}

// initialize build report
def buildReport = BuildReportFactory.createDefaultReport()

// print out build properties (good for debugging)
println("** Build properties at startup:")
println(properties.list())


//*
// Load list of files to build/scan
//*
def files = []

// check to see if a file/list was passed in to build
if (buildFile) {
	println("** Single file build : $buildFile")
	files = [buildFile]	
}
else if (buildList) {
	println("** Building files listed in $buildList")
       	files = new File(buildList) as List<String>
       
}       
else { // otherwise build the Mortgage Application file list
	buildList = "$properties.sourceDir/MortgageApplication/build/files.txt"
       	println("** Building files listed in $buildList")
      	files = new File(buildList) as List<String>
}


//*
// Scan the build files to collect and store new/updated dependency data
//*
println("** Scan the build list to collect dependency data")
def scanner = new DependencyScanner()
def logicalFiles = [] as List<LogicalFile>
files.each { file ->
    println("Scanning $file")
    def logicalFile = scanner.scan(file, properties.sourceDir)
    logicalFiles.add(logicalFile)
}

println("** Store the dependency data in repository collection '$properties.collection'")
def repo = new RepositoryClient().url(properties.url)
                                 .userId(properties.id)
                                 .passwordFile(new File("$properties.sourceDir/$properties.pwFile"))
                                 .forceSSLTrusted(true)

// create collection if needed
if (!repo.collectionExists(properties.collection))
    repo.createCollection(properties.collection)
    
repo.saveLogicalFiles(properties.collection, logicalFiles);
println(repo.getLastStatus())


//*
// Build programs in script order
//*
println("** Invoking scripts according to build order")
def buildCounter = 0
def buildOrder = ["BMSProcessing", "Compile", "LinkEdit", "CobolCompile"]
buildOrder.each { script ->
        // Use the ScriptMappings class to get the files mapped to the build script
	def buildFiles = ScriptMappings.getMappedList(script, files)
	def scriptFileName = "$properties.sourceDir/MortgageApplication/build/${script}.groovy"
	buildFiles.each { file ->
	        run(new File(scriptFileName), [file] as String[])
		buildCounter++
	}
}

// optionally execute IMS MFS builds
if (properties.BUILD_MFS.toBoolean()) {
	def script = "MFSGENUtility"
	def buildFiles = ScriptMappings.getMappedList(script, files)
	def scriptFileName = "$properties.sourceDir/MortgageApplication/build/${script}.groovy"
	buildFiles.each { file ->
		run(new File(scriptFileName), [file] as String[])
		buildCounter++
	}
}


//*
// Save Build Report artifacts.
//*
def buildReportEncoding = "UTF-8"
def jsonOutputFile = new File("${properties.buildDir}/BuildReport.json")

// Save build report in JSON format.
buildReport.save(jsonOutputFile, buildReportEncoding)

// Save a html file to render the json build report.
def htmlOutputFile = new File("${properties.buildDir}/BuildReport.html")
def htmlTemplate = null  // Use default HTML template.
def css = null       // Use default theme.
def renderScript = null  // Use default rendering.                       
def transformer = HtmlTransformer.getInstance()
transformer.transform(jsonOutputFile, htmlTemplate, css, renderScript, htmlOutputFile, buildReportEncoding)

//*
// Print end message
//*
def endTime = new Date()
def duration = TimeCategory.minus(endTime, startTime)
println("** Build finished at $endTime")
println("** Total files built : $buildCounter")
println("** Total build time  : $duration")
	




