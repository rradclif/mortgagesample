import com.ibm.team.dbb.repository.*
import com.ibm.team.dbb.dependency.*
import com.ibm.team.dbb.build.*

// receive passed arguments
def file = args[0]
println("* Building $file using ${this.class.getName()}.groovy script")

// define local properties
def properties = BuildProperties.getInstance()
def cobolPDS = "${properties.hlq}.COBOL"
def copybookPDS = "${properties.hlq}.COPYBOOK"
def objectPDS = "${properties.hlq}.OBJ"
def loadPDS = "${properties.hlq}.LOAD"
def dbrmPDS = "${properties.hlq}.DBRM"
def member = CopyToPDS.createMemberName(file)
def maxRC = 8

// define the BPXWDYN options for allocated temporary datasets
def tempCreateOptions = "tracks space(5,5) unit(vio) blksize(80) lrecl(80) recfm(f,b) new"

//*
// copy program to PDS 
//*
println("Copying ${properties.sourceDir}/$file to $cobolPDS($member)")
def copyProgram = new CopyToPDS().file(new File("${properties.sourceDir}/$file"))
                                 .dataset(cobolPDS)
                                 .member(member)
copyProgram.copy()


//*
//resolve program dependencies and copy to PDS
//*
println("Resolving dependencies for file $file and copying to $copybookPDS")
def repoClient = new RepositoryClient().url(properties.url)
                                       .userId(properties.id)
                                       .passwordFile(new File("$properties.sourceDir/$properties.pwFile"))
                                       .forceSSLTrusted(true)
def rule = new ResolutionRule().library("SYSLIB")
                               .path(new DependencyPath().collection(properties.collection)
                                                         .sourceDir(properties.sourceDir)
                                                         .directory("MortgageApplication/copybook"))	
def resolver = new DependencyResolver().repositoryClient(repoClient)
                                       .collection(properties.collection)
                                       .sourceDir(properties.sourceDir)
                                       .file(file)
                                       .rule(rule)
def deps = resolver.resolve()

// copy the dependencies to PDS
def copyDependencies = new CopyToPDS().dependencies(deps).dataset(copybookPDS)
copyDependencies.copy()
     

//*
// Compile and link-edit the build file
//*
println("Compiling and link editing program $file")	

// create the appropriate compile parm list
def compileParms = "LIB"
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    compileParms = "$compileParms,DYNAM,CICS"
}   
if (properties.getFileProperty("hasDB2", file).toBoolean()) {
    compileParms = "$compileParms,SQL"
}

// define the MVSExec command to compile the program
def compile = new MVSExec().file(file)
 			   .pgm("IGYCRCTL")
                           .parm(compileParms)
                           .attachx(true)

// add DD statements to the compile command
compile.dd(new DDStatement().name("SYSIN").dsn("$cobolPDS($member)").options("shr").report(true))
compile.dd(new DDStatement().name("SYSLIN").dsn("&&TEMPOBJ").options(tempCreateOptions).pass(true))
compile.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT2").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT3").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT4").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT5").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT6").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT7").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT8").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT9").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT10").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT11").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT12").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT13").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT14").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT15").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT16").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSUT17").options(tempCreateOptions))
compile.dd(new DDStatement().name("SYSMDECK").options(tempCreateOptions))

// add a syslib to the compile command with optional CICS concatenation
compile.dd(new DDStatement().name("SYSLIB").dsn(copybookPDS).options("shr"))
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    // create a DD statement without a name to concatenate to the last named DD
    compile.dd(new DDStatement().dsn(properties.SDFHCOB).options("shr"))
}

// add a tasklib to the compile command with optional CICS and DB2 concatenations
compile.dd(new DDStatement().name("TASKLIB").dsn(properties.SIGYCOMP).options("shr"))
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    compile.dd(new DDStatement().dsn(properties.SDFHLOAD).options("shr"))
}
if (properties.getFileProperty("hasDB2", file).toBoolean()) {
    compile.dd(new DDStatement().dsn(properties.SDSNLOAD).options("shr"))
}

// add optional DBRMLIB if build file contains DB2 code
if (properties.getFileProperty("hasDB2", file).toBoolean()) {
    compile.dd(new DDStatement().name("DBRMLIB").dsn("$dbrmPDS($member)").options("shr"))
}	

// add a copy command to the compile command to copy the SYSPRINT from the temporary dataset to an HFS log file
compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding))


// define the MVSExec command to link edit the program
def linkedit = new MVSExec().file(file)
	 	            .pgm("IEWBLINK")
	                    .parm("MAP,RENT,COMPAT(PM5)")
	                    
// add DD statements to the linkedit command
linkedit.dd(new DDStatement().name("SYSLMOD").dsn("$loadPDS($member)").options("shr").output(true))
linkedit.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSLIB").dsn(objectPDS).options("shr"))
linkedit.dd(new DDStatement().dsn(properties.SCEELKED).options("shr"))
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    linkedit.dd(new DDStatement().dsn(properties.SDFHLOAD).options("shr"))
}

// add a copy command to the linkedit command to append the SYSPRINT from the temporary dataset to the HFS log file
linkedit.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding).append(true))


// define and execute a simple MVSJob to handle passed temporary DDs between MVSExec commands
def job = new MVSJob().executable(compile)
                      .executable(linkedit)
                      .maxRC(maxRC)

job.execute()


  
