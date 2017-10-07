import com.ibm.team.dbb.repository.*
import com.ibm.team.dbb.dependency.*
import com.ibm.team.dbb.build.*

// receive passed arguments
def file = args[0]
println("* Building $file using ${this.class.getName()}.groovy script")

// define local properties
def properties = BuildProperties.getInstance()
def linkPDS = "${properties.hlq}.LINK"
def objectPDS = "${properties.hlq}.OBJ"
def loadPDS = "${properties.hlq}.LOAD"
def member = CopyToPDS.createMemberName(file)
def maxRC = 8

// define the BPXWDYN options for allocated temporary datasets
def tempCreateOptions = "tracks space(5,5) unit(vio) blksize(80) lrecl(80) recfm(f,b) new"

//*
// copy program to PDS 
//*
println("Copying ${properties.sourceDir}/$file to $linkPDS($member)")
def copyProgram = new CopyToPDS().file(new File("${properties.sourceDir}/$file"))
                                 .dataset(linkPDS)
                                 .member(member)
copyProgram.copy()


//*
// Link-edit the build file
//*
println("Link editing link file $file")	

// define the MVSExec command to link edit the program
def linkedit = new MVSExec().file(file)
	 	            .pgm("IEWBLINK")
	                    .parm("MAP,RENT,COMPAT(PM5)")
	                    
// add DD statements to the linkedit command
linkedit.dd(new DDStatement().name("SYSLIN").dsn("$linkPDS($member)").options("shr").report(true))
linkedit.dd(new DDStatement().name("SYSLMOD").dsn("$loadPDS($member)").options("shr").output(true))
linkedit.dd(new DDStatement().name("SYSPRINT").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSUT1").options(tempCreateOptions))
linkedit.dd(new DDStatement().name("SYSLIB").dsn(objectPDS).options("shr"))
linkedit.dd(new DDStatement().dsn(properties.SCEELKED).options("shr"))
linkedit.dd(new DDStatement().dsn(properties.SDFHLOAD).options("shr"))

// add a copy command to the linkedit command to append the SYSPRINT from the temporary dataset to the HFS log file
linkedit.copy(new CopyToHFS().ddName("SYSPRINT").file(new File("${properties.buildDir}/${member}.log")).encoding(properties.logEncoding))


// execute the link edit command
def rc = linkedit.execute()
if (rc > maxRC)
   throw new BuildException("Return code $rc from link editing $file exceeded maxRC $maxRC")

  
