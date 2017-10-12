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
// compile the build file
//*
println("Compiling build file $file")


// create the appropriate parm list
def parms = "LIB"
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    parms = "LIB,DYNAM,CICS"
}    

// define the MVSExec command to compile the program
def compile = new MVSExec().file(file)
 			   .pgm("IGYCRCTL")
                           .parm(parms)
                           .attachx(true)

// add DD statements to the MVSExec command
compile.dd(new DDStatement().name("SYSIN").dsn("$cobolPDS($member)").options("shr").report(true))
compile.dd(new DDStatement().name("SYSLIN").dsn("$objectPDS($member)").options("shr").output(true))
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

// add a syslib to the MVSExec command with optional CICS concatenation
compile.dd(new DDStatement().name("SYSLIB").dsn(copybookPDS).options("shr"))
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    // create a DD statement without a name to concatenate to the last named DD added to the MVSExec
    compile.dd(new DDStatement().dsn(properties.SDFHCOB).options("shr"))
}

// add a tasklib to the MVSExec command with optional CICS concatenation
compile.dd(new DDStatement().name("TASKLIB").dsn(properties.SIGYCOMP).options("shr"))
if (properties.getFileProperty("hasCICS", file).toBoolean()) {
    // create a DD statement without a name to concatenate to the last named DD added to the MVSExec
    compile.dd(new DDStatement().dsn(properties.SDFHLOAD).options("shr"))
}

// add a copy command to the MVSExec command to copy the SYSPRINT from the temporary dataset to an HFS log file
compile.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding))

// execute the MVSExec compile command
def rc = compile.execute()
if (rc > maxRC)
   throw new BuildException("Return code $rc from compiling $file exceeded maxRC $maxRC")
	


  
