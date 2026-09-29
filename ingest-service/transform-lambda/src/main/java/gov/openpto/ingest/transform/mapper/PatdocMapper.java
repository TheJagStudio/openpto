package gov.openpto.ingest.transform.mapper;

import static gov.openpto.ingest.transform.mapper.MapperSupport.blankToNull;
import static gov.openpto.ingest.transform.mapper.MapperSupport.date;
import static gov.openpto.ingest.transform.mapper.MapperSupport.joinName;
import static gov.openpto.ingest.transform.mapper.MapperSupport.number;
import static gov.openpto.ingest.transform.mapper.MapperSupport.require;
import static gov.openpto.ingest.transform.mapper.MapperSupport.text;

import gov.openpto.ingest.transform.MappingException;
import gov.openpto.ingest.transform.model.Citation;
import gov.openpto.ingest.transform.model.Claim;
import gov.openpto.ingest.transform.model.Party;
import gov.openpto.ingest.transform.model.PatentRecord;
import gov.openpto.ingest.transform.xml.XmlNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps legacy 2001–2004 "red book" grants ({@code <PATDOC>}, ST.32 / DTD 2.4–2.5) whose field names are
 * WIPO INID-style codes: B110 document number, B130 kind, B140 date of grant, B210 application number,
 * B220 filing date, B540 title, B560 citations, B721 inventors, B731 assignees, B746 examiner,
 * SDOAB abstract and SDOCL claims.
 */
public final class PatdocMapper implements RecordMapper {

    @Override
    public String identify(XmlNode root) {
        XmlNode sdobi = root.child("SDOBI");
        return sdobi == null ? null : MapperSupport.patentNumber(text(sdobi, "B100/B110"), text(sdobi, "B100/B130"));
    }

    @Override
    public PatentRecord map(XmlNode root, String ingestJobId) {
        XmlNode sdobi = root.child("SDOBI");
        if (sdobi == null) {
            throw new MappingException(null, "Missing <SDOBI> bibliographic section");
        }
        String docNumber = require(text(sdobi, "B100/B110"), null, "B110 document number");
        String kind = text(sdobi, "B100/B130");
        String patentNumber = MapperSupport.patentNumber(docNumber, kind);
        LocalDate grantDate = date(text(sdobi, "B100/B140"));
        LocalDate filingDate = require(date(text(sdobi, "B200/B220")), patentNumber, "B220 filing date");
        String title = require(text(sdobi, "B500/B540"), patentNumber, "B540 title");
        String type = MapperSupport.patentType(null, docNumber, kind);

        LocalDate priority = filingDate;
        for (XmlNode b320 : sdobi.descendants("B320")) {
            LocalDate d = date(b320.text());
            if (d != null && d.isBefore(priority)) {
                priority = d;
            }
        }
        LocalDate expiration = "DESIGN".equals(type)
                ? (grantDate == null ? null : grantDate.plusYears(14))
                : ("REISSUE".equals(type) ? null : filingDate.plusYears(20));

        XmlNode examiner = sdobi.find("B700/B745/B746/PARTY-US/NAM");
        XmlNode abs = root.child("SDOAB");
        return new PatentRecord(
                patentNumber,
                MapperSupport.applicationNumber(text(sdobi, "B200/B210")),
                title,
                type,
                "GRANTED",
                filingDate,
                grantDate,
                grantDate,
                null,
                List.of(),
                abs == null ? null : blankToNull(abs.text()),
                claims(root),
                citations(sdobi),
                parties(sdobi, "B720", "B721"),
                parties(sdobi, "B730", "B731"),
                priority,
                expiration,
                examiner == null ? null : joinName(text(examiner, "FNM"), text(examiner, "SNM")),
                text(sdobi, "B700/B745/B748US"),
                ingestJobId,
                PatentRecord.SOURCE_INGEST);
    }

    private static List<Claim> claims(XmlNode root) {
        List<Claim> result = new ArrayList<>();
        XmlNode cl = root.find("SDOCL/CL");
        if (cl == null) {
            return result;
        }
        int position = 0;
        for (XmlNode clm : cl.children("CLM")) {
            position++;
            Integer num = number(clm.attr("ID"));
            XmlNode ref = clm.firstDescendant("CLREF");
            Integer dependsOn = ref == null ? null : number(ref.attr("ID"));
            result.add(new Claim(num == null ? position : num, clm.text(), dependsOn == null, dependsOn));
        }
        return result;
    }

    private static List<Citation> citations(XmlNode sdobi) {
        List<Citation> result = new ArrayList<>();
        for (XmlNode b561 : sdobi.descendants("B561")) {
            XmlNode doc = b561.find("PCIT/DOC");
            String number = text(doc, "DNUM");
            if (number == null) {
                continue;
            }
            String country = text(b561, "PCIT/CTRY");
            String cited = country == null || "US".equalsIgnoreCase(country)
                    ? MapperSupport.patentNumber(number, text(doc, "KIND"))
                    : country + number;
            String citedBy = text(b561, "CITED-BY");
            result.add(new Citation(cited, "2".equals(citedBy) ? "APPLICANT" : "EXAMINER"));
        }
        return result;
    }

    private static List<Party> parties(XmlNode sdobi, String group, String item) {
        List<Party> result = new ArrayList<>();
        XmlNode b700 = sdobi.child("B700");
        if (b700 == null) {
            return result;
        }
        for (XmlNode groupNode : b700.children(group)) {
            for (XmlNode entry : groupNode.children(item)) {
                XmlNode party = entry.child("PARTY-US");
                if (party == null) {
                    party = entry.child("PARTY-NUS");
                }
                if (party == null) {
                    continue;
                }
                XmlNode nam = party.child("NAM");
                String name = text(nam, "ONM");
                if (name == null) {
                    name = joinName(text(nam, "FNM"), text(nam, "SNM"));
                }
                if (name == null) {
                    continue;
                }
                XmlNode adr = party.child("ADR");
                String state = text(adr, "STATE");
                String country = text(adr, "CTRY");
                if (country == null && state != null) {
                    country = "US";
                }
                result.add(new Party(name, text(adr, "CITY"), state, country));
            }
        }
        return result;
    }
}
