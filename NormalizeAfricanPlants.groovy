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

Model model = ModelFactory.createDefaultModel()

new File("africanplants/qryFLOPO_KnowledgeBase.txt").splitEachLine(",") { line ->

  if (line[0].indexOf("custFotoArtMerkmalWertID")==-1) {
    def name = line[1]?.replaceAll("\"","")
    def cl = line[6]?.replaceAll("\"","")
    def gbif = line[5]?.replaceAll("\"","")
    def source = line[3]?.replaceAll("\"","")
    model.add(model.createStatement(model.createResource(gbif), RDFS.label, model.createLiteral(name, "en")))
    model.add(model.createStatement(model.createResource(gbif), model.createProperty("http://purl.obolibrary.org/obo/", "TAXRANK_1000000"), model.createResource("http://purl.obolibrary.org/obo/TAXRANK_0000006")))
    def content = model.createStatement(model.createResource(gbif), model.createProperty("http://purl.obolibrary.org/obo/", "RO_0002200"), model.createResource(cl))
    model.add(content)
    def reifiedContent = model.createReifiedStatement(content)
    def evidence = model.createResource() // anonymous evidence object
    model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence_code"), model.createResource("http://purl.obolibrary.org/obo/ECO_0000033")))
    model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence_code"), model.createResource("http://purl.obolibrary.org/obo/ECO_0000218")))
    model.add(model.createStatement(evidence, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_source"), model.createLiteral(source, "en")))
    
    model.add(model.createStatement(reifiedContent, model.createProperty("http://aber-owl.net/ontology/FLOPO#", "has_evidence"), evidence))
  }
}

def fout = new BufferedWriter(new FileWriter(new File("flopo-annotations-african-plants.ttl")))
model.write(fout, "TTL")
fout.flush()
fout.close()

