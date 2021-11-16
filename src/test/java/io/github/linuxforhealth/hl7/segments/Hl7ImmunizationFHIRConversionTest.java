/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.linuxforhealth.hl7.segments;


import io.github.linuxforhealth.fhir.FHIRContext;
import io.github.linuxforhealth.hl7.ConverterOptions;
import io.github.linuxforhealth.hl7.HL7ToFHIRConverter;

import io.github.linuxforhealth.hl7.segments.util.ResourceUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class Hl7ImmunizationFHIRConversionTest {
  private static FHIRContext context = new FHIRContext(true, false);
  private static final ConverterOptions OPTIONS = new ConverterOptions.Builder().withValidateResource().build();

  @Test
  public void testImmunization() throws IOException {


    String hl7VUXmessageRep = "MSH|^~\\&|MYEHR2.5|RI88140101|KIDSNET_IFL|RIHEALTH|20130531||VXU^V04^VXU_V04|20130531RI881401010105|P|2.5.1|||NE|AL||||||RI543763\r"
            + "PID|1||12345^^^^MR||TestPatient^Jane^^^^^L||||||\r"
            + "ORC|RE||197027|||||||^Clerk^Myron||MD67895^Pediatric^MARY^^^^MD^^RIA|||||RI2050\r"
            + "RXA|0|1|20130531|20130531|48^HIB PRP-T^CVX|0.5|ML^^ISO+||00^new immunization record^NIP001|^Sticker^Nurse|^^^RI2050||||33k2a|20131210|PMC^sanofi^MVX|PATOBJ||CP|A\r"
            + "RXR|C28161^IM^NCIT^IM^INTRAMUSCULAR^HL70162|RT^right thigh^HL70163\r"
            + "OBX|1|CE|64994-7^vaccine fund pgm elig cat^LN|1|V02^VFC eligible Medicaid/MedicaidManaged Care^HL70064||||||F|||20130531|||VXC40^per imm^CDCPHINVS\r";

    HL7ToFHIRConverter ftv = new HL7ToFHIRConverter();
    String json = ftv.convert(hl7VUXmessageRep, OPTIONS);
    IBaseResource bundleResource = context.getParser().parseResource(json);
    assertThat(bundleResource).isNotNull();
    Bundle b = (Bundle) bundleResource;
    List<Bundle.BundleEntryComponent> e = b.getEntry();
    List<Resource> immu = e.stream().filter(v -> ResourceType.Immunization == v.getResource().getResourceType())
            .map(Bundle.BundleEntryComponent::getResource).collect(Collectors.toList());
    assertThat(immu).hasSize(1);
    Immunization resource = ResourceUtils.getResourceImmunization(immu.get(0),context);
    assertThat(resource).isNotNull();

    assertThat(resource.getStatus().getDisplay()).isEqualTo("completed"); // RXA.20
    assertThat(resource.getIdentifier().get(0).getValue()).isEqualTo("48-CVX"); // RXA.5.1 + 5.3
    assertThat(resource.getIdentifier().get(0).getSystem()).isEqualTo("urn:id:extID");
    assertThat(resource.getVaccineCode().getCoding().get(0).getSystem())
            .isEqualTo("http://hl7.org/fhir/sid/cvx"); // RXA.5.3
    assertThat(resource.getVaccineCode().getCoding().get(0).getCode()).isEqualTo("48"); // RXA.5.1
    assertThat(resource.getVaccineCode().getText()).isEqualTo("HIB PRP-T"); // RXA.5.2
    assertThat(resource.getOccurrence()).hasToString("DateTimeType[2013-05-31]"); // RXA.3

    assertThat(resource.getReportOrigin().getCoding().get(0).getSystem()).isEqualTo("urn:id:NIP001");// RXA.9.3
    assertThat(resource.getReportOrigin().getCoding().get(0).getCode()).isEqualTo("00");// RXA.9.
    assertThat(resource.getReportOrigin().getCoding().get(0).getDisplay()).isEqualTo("new immunization record"); // RXA.9.2
    assertThat(resource.getReportOrigin().getText()).isEqualTo("new immunization record");// RXA.9.2
    assertThat(resource.getManufacturer().isEmpty()).isFalse(); // RXA.17

    assertThat(resource.getLotNumber()).isEqualTo("33k2a"); // RXA.15
    assertThat(resource.getExpirationDate()).isEqualTo("2013-12-10"); // RXA.16

    //dose Quantity with an unknown system
    assertThat(resource.hasDoseQuantity()).isTrue();
    assertThat(resource.getDoseQuantity().getValue().toString()).isEqualTo("0.5");
    assertThat(resource.getDoseQuantity().getUnit()).isEqualTo("ML");
    assertThat(resource.getDoseQuantity().getSystem()).isEqualTo("urn:id:ISO+");

    String requesterRef1 = resource.getPerformer().get(0).getActor().getReference();
    Practitioner practBundle1 = ResourceUtils.getSpecificPractitionerFromBundle(b, requesterRef1);
    assertThat(resource.getPerformer()).hasSize(2);
    assertThat(resource.getPerformer().get(0).getFunction().getCoding().get(0).getCode())
            .isEqualTo("OP"); // ORC.12
    assertThat(resource.getPerformer().get(0).getFunction().getText())
            .isEqualTo("Ordering Provider"); // ORC.12
    assertThat(resource.getPerformer().get(0).getActor().getReference().   isEmpty()).isFalse(); // ORC.12
    assertThat(practBundle1.getNameFirstRep().getText()).isEqualTo("MARY Pediatric");
    assertThat(practBundle1.getNameFirstRep().getFamily()).isEqualTo("Pediatric");
    assertThat(practBundle1.getNameFirstRep().getGiven().get(0).toString()).isEqualTo("MARY");
    assertThat(practBundle1.getIdentifierFirstRep().getValue()).isEqualTo("MD67895");

    String requesterRef2 = resource.getPerformer().get(1).getActor().getReference();
    Practitioner practBundle2 = ResourceUtils.getSpecificPractitionerFromBundle(b, requesterRef2);
    assertThat(resource.getPerformer().get(1).getFunction().getCoding().get(0).getCode())
            .isEqualTo("AP"); // RXA.10
    assertThat(resource.getPerformer().get(1).getFunction().getText())
            .isEqualTo("Administering Provider"); // RXA.10
    assertThat(resource.getPerformer().get(1).getActor().isEmpty()).isFalse(); // RXA.10
    assertThat(practBundle2.getNameFirstRep().getText()).isEqualTo("Nurse Sticker");
    assertThat(practBundle2.getNameFirstRep().getFamily()).isEqualTo("Sticker");
    assertThat(practBundle2.getNameFirstRep().getGiven().get(0).toString()).isEqualTo("Nurse");

    //Status reason RXA.18 with code from act reason
    Coding statReason = resource.getStatusReason().getCoding().get(0);

    assertThat(statReason.hasCode());
    assertThat(statReason.getCode()).isEqualTo("PATOBJ");
    assertThat(statReason.getDisplay()).isEqualTo("patient objection");
    assertThat(statReason.getSystem()).isEqualTo("http://terminology.hl7.org/CodeSystem/v3-ActReason");

    // Test that a ServiceRequest is not created for VXU_V04
    List<Resource> serviceRequestList = e.stream()
            .filter(v -> ResourceType.ServiceRequest == v.getResource().getResourceType())
            .map(Bundle.BundleEntryComponent::getResource).collect(Collectors.toList());
    // Confirm that a serviceRequest was not created.
    assertThat(serviceRequestList).isEmpty();

    // Test should only return RXA.10, ORC.12  is empty
    hl7VUXmessageRep = "MSH|^~\\&|MYEHR2.5|RI88140101|KIDSNET_IFL|RIHEALTH|20130531||VXU^V04^VXU_V04|20130531RI881401010105|P|2.5.1|||NE|AL||||||RI543763\r"
            + "PID|1||12345^^^^MR||TestPatient^Jane^^^^^L||||||\r"
            + "ORC|RE||197027|||||||^Clerk^Myron|||||||RI2050\r"
            + "RXA|0|1|20130531|20130531|48^HIB PRP-T^CVX|0.5|ML^^^||00^new immunization record^NIP001|^Sticker^Nurse|^^^RI2050||||33k2a|20131210|PMC^sanofi^MVX|NIP002^patient refusal||CP|A\r"
            + "RXR|C28161^IM^NCIT^IM^INTRAMUSCULAR^HL70162|RT^right thigh^HL70163\r"
            + "OBX|1|CE|64994-7^vaccine fund pgm elig cat^LN|1|V02^VFC eligible Medicaid/MedicaidManaged Care^HL70064||||||F|||20130531|||VXC40^per imm^CDCPHINVS\r";

    Immunization immunization1 = ResourceUtils.getImmunization(hl7VUXmessageRep);

    assertThat(immunization1.getPerformer()).hasSize(1);
    assertThat(immunization1.getPerformer().get(0).getFunction().getCodingFirstRep().getCode()).isEqualTo("AP");// RXA.10
    assertThat(immunization1.getPerformer().get(0).getFunction().getText()).isEqualTo("Administering Provider"); // RXA.10

    //dose Quantity without a system
    assertThat(immunization1.hasDoseQuantity()).isTrue();
    assertThat(immunization1.getDoseQuantity().getValue().toString()).isEqualTo("0.5");
    assertThat(immunization1.getDoseQuantity().getUnit()).isEqualTo("ML");
    assertThat(immunization1.getDoseQuantity().getSystem()).isNull();

    //Status reason RXA.18 with code from CWE
    statReason = immunization1.getStatusReason().getCoding().get(0);

    assertThat(statReason.hasCode());
    assertThat(statReason.getCode()).isEqualTo("NIP002");
    assertThat(statReason.getDisplay()).isEqualTo("patient refusal");
    assertThat(statReason.getSystem()).isNull();


    // Test should only return RXA.10, ORC.12  is empty
    hl7VUXmessageRep = "MSH|^~\\&|MYEHR2.5|RI88140101|KIDSNET_IFL|RIHEALTH|20130531||VXU^V04^VXU_V04|20130531RI881401010105|P|2.5.1|||NE|AL||||||RI543763\r"
            + "PID|1||12345^^^^MR||TestPatient^Jane^^^^^L||||||\r"
            + "ORC|RE||197027|||||||^Clerk^Myron|||||||RI2050\r"
            + "RXA|0|1|20130531|20130531|48^HIB PRP-T^CVX|0.5|ML^^UCUM||00^new immunization record^NIP001|^Sticker^Nurse|^^^RI2050||||33k2a|20131210|PMC^sanofi^MVX|||CP|A\r"
            + "RXR|C28161^IM^NCIT^IM^INTRAMUSCULAR^HL70162|RT^right thigh^HL70163\r"
            + "OBX|1|CE|64994-7^vaccine fund pgm elig cat^LN|1|V02^VFC eligible Medicaid/MedicaidManaged Care^HL70064||||||F|||20130531|||VXC40^per imm^CDCPHINVS\r";

    Immunization immunization2 = ResourceUtils.getImmunization(hl7VUXmessageRep);

    //dose Quantity with a known system
    assertThat(immunization2.hasDoseQuantity()).isTrue();
    assertThat(immunization2.getDoseQuantity().getValue().toString()).isEqualTo("0.5");
    assertThat(immunization2.getDoseQuantity().getUnit()).isEqualTo("ML");
    assertThat(immunization2.getDoseQuantity().getSystem()).isEqualTo("http://unitsofmeasure.org");
  }
  // TODO: 10/15/21 RXA-9 (also mapped to primarySource)
  //  RXA-18 statusReason
  //  RXA-20 (status, statusReason, isSubpotent)
}
