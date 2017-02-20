@Grab(group='org.apache.jena', module='jena-core', version='3.0.0')
@Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.2')
@Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.0.2')
@Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.0.2')
@Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.0.2')


import groovy.json.*
import org.apache.jena.vocabulary.*
import org.apache.jena.rdfxml.xmlinput.*
import org.apache.jena.rdf.model.*

import org.semanticweb.owlapi.io.*
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary


String url = 'http://api.gbif.org/v1/species/match?name='
URLEncoder enc = new URLEncoder()
def jsonSlurper = new JsonSlurper()


OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
OWLDataFactory fac = manager.getOWLDataFactory()
def factory = fac
OWLOntology ont = manager.loadOntologyFromOntologyDocument(IRI.create("http://purl.obolibrary.org/obo/flopo.owl"))

ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)

OWLReasonerFactory f1 = new ElkReasonerFactory()
OWLReasoner reasoner = f1.createReasoner(ont,config)
reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)

def objects = [:]

def done = [:]
new File("clean-all-eq.txt").splitEachLine("\t") { line ->
  if (line[2] && line[2].size()>0 && line[4] && line[4].size()>0) {
    def po = "http://purl.obolibrary.org/obo/"+line[2].replaceAll(":","_")
    def pato = "http://purl.obolibrary.org/obo/"+line[4].replaceAll(":","_")
    def cl = reasoner.getEquivalentClasses(
      fac.getOWLObjectSomeValuesFrom(
	fac.getOWLObjectProperty(
	  IRI.create("http://purl.obolibrary.org/obo/BFO_0000051")), fac.getOWLObjectIntersectionOf(
	    fac.getOWLClass(IRI.create(po)), fac.getOWLObjectSomeValuesFrom(
	      fac.getOWLObjectProperty(
		IRI.create("http://purl.obolibrary.org/obo/RO_0000053")), fac.getOWLClass(IRI.create(pato))))))
    def flopo = null
    if (cl.size()>0) {
      flopo = cl.getRepresentativeElement().toString()?.replaceAll("<","")?.replaceAll(">","")
    }
    //  def flopo = line[-1]?.replaceAll("http://phenomebrowser.net/plant-phenotype.owl#FLOPO:","http://purl.obolibrary.org/obo/FLOPO_")?.replaceAll("<","")?.replaceAll(">","")
    //  println flopo
    def str = line[0]?.toLowerCase()?.replaceAll(",",";")
    def family = ""
    def genus = ""
    def species = ""
    if (str.indexOf("family:")>-1) {
      family = str.substring(str.indexOf("family: ")+8)
      family = family.substring(0, family.indexOf(";")).trim()
    }
    if (str.indexOf("genus:")>-1) {
      genus = str.substring(str.indexOf("genus: ")+6)
      genus = genus.substring(0, genus.indexOf(";")).trim()
    }
    if (str.indexOf("species:")>-1) {
      species = str.substring(str.indexOf("species: ")+8)
      if (species.indexOf(";")>-1) {
	species = species.substring(0, species.indexOf(";")).trim()
      }
    }
    def query = ""
    if (species.length()>0) {
      query = enc.encode(genus+" "+species)
    } else if (family.length()>0) {
      query = enc.encode(family)
    }
    def obj = null
    if (! (query in done)) {
      //    println query
      obj = jsonSlurper.parse(new URL(url+query))
      done[query] = obj
    }
    obj = done[query]
    if (obj.confidence >= 95) {
      def key = obj.usageKey
      if (objects[key] == null) {
	obj.phenotypes = new TreeSet()
	objects[key] = obj
      }
      obj = objects[key]
      if (flopo) {
	obj.phenotypes.add(flopo)
      }
    }
  }
}

Model model = ModelFactory.createDefaultModel()

objects.each { key, obj ->
  if (key && key!=null) {
    def k = "http://www.gbif.org/species/$key"
    model.add(model.createStatement(model.createResource(k), RDFS.label, model.createLiteral(obj.canonicalName, "en")))
    //    println "<$k> <http://www.w3.org/2000/01/rdf-schema#label> \""+obj.canonicalName+"\"@en ."
    if (obj.rank) {
      // 'has rank' relation
      if (obj.rank.toLowerCase() == "species") {
	model.add(model.createStatement(model.createResource(k), model.createProperty("http://purl.obolibrary.org/obo/", "TAXRANK_1000000"), model.createResource("http://purl.obolibrary.org/obo/TAXRANK_0000006")))
	//	println "<$k> <http://purl.obolibrary.org/obo/TAXRANK_1000000> <http://purl.obolibrary.org/obo/TAXRANK_0000006> ."
      } else if (obj.rank.toLowerCase() == "family") {
	model.add(model.createStatement(model.createResource(k), model.createProperty("http://purl.obolibrary.org/obo/", "TAXRANK_1000000"), model.createResource("http://purl.obolibrary.org/obo/TAXRANK_0000004")))
	//	println "<$k> <http://purl.obolibrary.org/obo/TAXRANK_1000000> <http://purl.obolibrary.org/obo/TAXRANK_0000004> ."
      }
    }
    obj.phenotypes.each { p ->
      def content = model.createStatement(model.createResource(k), model.createProperty("http://purl.obolibrary.org/obo/", "RO_0002200"), model.createResource(p))
      model.add(content)
      def reifiedContent = model.createReifiedStatement(content)
      def evidence = model.createResource() // anonymous evidence object
      model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence_code"), model.createResource("http://purl.obolibrary.org/obo/ECO_0000034")))
      model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence_code"), model.createResource("http://purl.obolibrary.org/obo/ECO_0000203")))
      model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_source"), model.createLiteral("PMID:27842607", "en")))

      model.add(model.createStatement(reifiedContent, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence"), evidence))
    }
  }
}
def fout = new BufferedWriter(new FileWriter(new File("flopo-annotations-new.ttl")))
model.write(fout, "TTL")
fout.flush()
fout.close()

