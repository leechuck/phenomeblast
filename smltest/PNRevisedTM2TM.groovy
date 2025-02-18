import slib.sglib.model.impl.graph.elements.Edge
import org.openrdf.model.vocabulary.*
import slib.sglib.io.conf.GDataConf
import slib.sglib.io.loader.*
import slib.sglib.io.util.GFormat
import slib.sglib.model.graph.G
import slib.sglib.model.impl.graph.memory.GraphMemory
import slib.sglib.model.impl.repo.URIFactoryMemory
import slib.sglib.model.repo.URIFactory
import slib.sml.sm.core.engine.SM_Engine
import slib.sml.sm.core.metrics.ic.utils.*
import slib.sml.sm.core.utils.*
import slib.utils.ex.SLIB_Exception
import slib.sglib.io.loader.bio.obo.*
import org.openrdf.model.URI
import slib.sglib.algo.graph.extraction.rvf.instances.InstancesAccessor
import slib.sglib.algo.graph.extraction.rvf.instances.impl.InstanceAccessor_RDF_TYPE
import slib.sglib.algo.graph.utils.*
import slib.utils.impl.Timer

def cli = new CliBuilder()
cli.with {
usage: 'Self'
  h longOpt:'help', 'this information'
  a longOpt:'annotation-file', 'Text-mined phenotype file', args:1, required:true
  o longOpt:'output', 'output file', args:1, required:true
  d longOpt:'phenotype-annotations', 'phenotype annotation output file', args:1, required:true
  //  "1" longOpt:'pmi', 'min PMI', args:1, required:true
  //  "2" longOpt:'lmi', 'min LMI', args:1, required:true
}

def opt = cli.parse(args)
if( !opt ) {
  //  cli.usage()
  return
}
if( opt.h ) {
    cli.usage()
    return
}

//def minpmi = new Double(opt."1")
//def minlmi = new Double(opt."2")

def mpfile = new File(opt.a)

def fout = new PrintWriter(new BufferedWriter(new FileWriter(opt.o)))
def pout = new PrintWriter(new BufferedWriter(new FileWriter(opt.d)))

def URI = "http://phenomebrowser.net/smltest/"
URIFactory factory = URIFactoryMemory.getSingleton()
URI graph_uri = factory.createURI(URI)
//factory.loadNamespacePrefix("HP", graph_uri.toString())
G graph = new GraphMemory(graph_uri)

GDataConf graphconf = new GDataConf(GFormat.RDF_XML, "monarch/monarch-inferred.owl")
GraphLoaderGeneric.populate(graphconf, graph)

URI virtualRoot = factory.createURI("http://phenomebrowser.net/smltest/virtualRoot");
graph.addV(virtualRoot);
        
// We root the graphs using the virtual root as root
GAction rooting = new GAction(GActionType.REROOTING);
rooting.addParameter("root_uri", virtualRoot.stringValue());
GraphActionExecutor.applyAction(factory, rooting, graph);

// remove all instances
Set removeE = new LinkedHashSet()
graph.getE().each { it ->
  String es = it.toString();
  if ( es.indexOf("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")>-1 ) {
    removeE.add( it );
  }
}               
removeE.each { graph.removeE(it) }

// adding instances
mpfile.splitEachLine("\t") { line ->
  def id = line[0]
  def iduri = factory.createURI(URI+id)
  def mp = line[1]?.trim()
  mp = mp?.replaceAll(":","_")
  def mpuri = factory.createURI("http://purl.obolibrary.org/obo/"+mp)
  if (mp && id && iduri && mpuri && mp.length()>0 && id.length()>0) {
    Edge e = new Edge(iduri, RDF.TYPE, mpuri);
    graph.addE(e)
  }
}

ICconf icConf = new IC_Conf_Corpus("Resnik", SMConstants.FLAG_IC_ANNOT_RESNIK_1995)
SMconf smConfGroupwise = new SMconf("SimGIC", SMConstants.FLAG_SIM_GROUPWISE_DAG_GIC)
smConfGroupwise.setICconf(icConf)

SM_Engine engine = new SM_Engine(graph)

println "Computing similarity..."
InstancesAccessor ia = new InstanceAccessor_RDF_TYPE(graph)

//omim2pheno.each { omim, phenos2 ->
engine.getInstances().each { mgi1 ->
  Set phenos2 = ia.getDirectClass(mgi1)
  phenos2.each { p ->
    pout.print("$mgi1\t$p")
    engine.getAncestorsInc(p).each { a ->
      pout.print("\t"+a)
    }
    pout.println("")
  }
  if (phenos2.size() > 0) {
    println "Processing $mgi1..."
    engine.getInstances().each { mgi ->
      Set mgiSet = ia.getDirectClass(mgi)
      def sim = engine.computeGroupwiseStandaloneSim(smConfGroupwise, phenos2, mgiSet)
      fout.println("$mgi1\t$mgi\t$sim")
    }
  }
}
fout.flush()
fout.close()
pout.flush()
pout.close()
