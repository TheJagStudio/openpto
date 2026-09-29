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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps USPTO "ICE" XML (v4.x) patent documents: {@code us-patent-grant} (grant red book) and
 * {@code us-patent-application} / {@code us-patent-application-publication} (pre-grant publications).
 */
public final class IcePatentMapper implements RecordMapper {

    private final boolean grant;

    public IcePatentMapper(boolean grant) {
        this.grant = grant;
    }

    @Override
    public String identify(XmlNode root) {
        XmlNode pub = bibliographic(root) == null ? null : bibliographic(root).find("publication-reference/document-id");
        if (pub == null) {
            return null;
        }
        return MapperSupport.patentNumber(text(pub, "doc-number"), text(pub, "kind"));
    }

    @Override
    public PatentRecord map(XmlNode root, String ingestJobId) {
        XmlNode bib = bibliographic(root);
        if (bib == null) {
            throw new MappingException(null, "Missing <us-bibliographic-data-" + (grant ? "grant" : "application") + ">");
        }
        XmlNode pub = bib.find("publication-reference/document-id");
        String docNumber = require(text(pub, "doc-number"), null, "publication-reference/document-id/doc-number");
        String kind = text(pub, "kind");
        String patentNumber = MapperSupport.patentNumber(docNumber, kind);

        XmlNode applRef = bib.child("application-reference");
        XmlNode appl = applRef == null ? null : applRef.child("document-id");
        String applicationNumber = MapperSupport.applicationNumber(text(appl, "doc-number"));
        LocalDate filingDate = require(date(text(appl, "date")), patentNumber, "application-reference/document-id/date");
        String title = require(text(bib, "invention-title"), patentNumber, "invention-title");
        String type = MapperSupport.patentType(applRef == null ? null : applRef.attr("appl-type"), docNumber, kind);
        LocalDate publicationDate = date(text(pub, "date"));

        List<String> cpcCodes = cpcCodes(bib);
        LocalDate grantDate = grant ? publicationDate : null;
        LocalDate priorityDate = priorityDate(bib, filingDate);

        XmlNode examiner = bib.find("examiners/primary-examiner");
        return new PatentRecord(
                patentNumber,
                applicationNumber,
                title,
                type,
                grant ? "GRANTED" : "PENDING",
                filingDate,
                grantDate,
                publicationDate,
                cpcCodes.isEmpty() ? null : cpcCodes.get(0),
                cpcCodes,
                abstractText(root),
                claims(root),
                citations(bib),
                inventors(bib),
                assignees(bib),
                priorityDate,
                grant ? expiration(type, filingDate, grantDate, bib) : null,
                examiner == null ? null : joinName(text(examiner, "first-name"), text(examiner, "last-name")),
                text(examiner, "department"),
                ingestJobId,
                PatentRecord.SOURCE_INGEST);
    }

    private XmlNode bibliographic(XmlNode root) {
        XmlNode bib = root.child(grant ? "us-bibliographic-data-grant" : "us-bibliographic-data-application");
        return bib != null ? bib : root.child(grant ? "us-bibliographic-data-application" : "us-bibliographic-data-grant");
    }

    static List<String> cpcCodes(XmlNode bib) {
        Set<String> codes = new LinkedHashSet<>();
        XmlNode cpc = bib.child("classifications-cpc");
        if (cpc != null) {
            for (String group : List.of("main-cpc", "further-cpc")) {
                for (XmlNode c : cpc.findAll(group + "/classification-cpc")) {
                    String code = cpcCode(c);
                    if (code != null) {
                        codes.add(code);
                    }
                }
            }
        }
        return new ArrayList<>(codes);
    }

    private static String cpcCode(XmlNode c) {
        String section = text(c, "section");
        String cls = text(c, "class");
        String subclass = text(c, "subclass");
        String mainGroup = text(c, "main-group");
        String subgroup = text(c, "subgroup");
        if (section == null || cls == null || subclass == null) {
            return null;
        }
        String code = section + cls + subclass;
        if (mainGroup != null) {
            code += " " + mainGroup + "/" + (subgroup == null ? "00" : subgroup);
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private static LocalDate priorityDate(XmlNode bib, LocalDate filingDate) {
        LocalDate earliest = filingDate;
        for (XmlNode claim : bib.findAll("priority-claims/priority-claim")) {
            LocalDate d = date(text(claim, "date"));
            if (d != null && (earliest == null || d.isBefore(earliest))) {
                earliest = d;
            }
        }
        return earliest;
    }

    /** 20 years from filing (+ patent term adjustment) for utility/plant, 15 years from grant for designs. */
    private static LocalDate expiration(String type, LocalDate filingDate, LocalDate grantDate, XmlNode bib) {
        if ("DESIGN".equals(type)) {
            return grantDate == null ? null : grantDate.plusYears(15);
        }
        if ("REISSUE".equals(type) || filingDate == null) {
            return null;
        }
        Integer extension = number(text(bib, "us-term-of-grant/us-term-extension"));
        return filingDate.plusYears(20).plusDays(extension == null ? 0 : extension);
    }

    private static String abstractText(XmlNode root) {
        XmlNode abs = root.child("abstract");
        return abs == null ? null : blankToNull(abs.text());
    }

    static List<Claim> claims(XmlNode root) {
        List<Claim> result = new ArrayList<>();
        XmlNode claims = root.child("claims");
        if (claims == null) {
            return result;
        }
        int position = 0;
        for (XmlNode claim : claims.children("claim")) {
            position++;
            Integer num = number(claim.attr("num"));
            if (num == null) {
                num = number(claim.attr("id"));
            }
            XmlNode ref = claim.firstDescendant("claim-ref");
            Integer dependsOn = ref == null ? null : number(ref.attr("idref"));
            result.add(new Claim(num == null ? position : num, claim.text(), dependsOn == null, dependsOn));
        }
        return result;
    }

    private static List<Citation> citations(XmlNode bib) {
        XmlNode refs = bib.child("us-references-cited");
        if (refs == null) {
            refs = bib.child("references-cited");
        }
        List<Citation> result = new ArrayList<>();
        if (refs == null) {
            return result;
        }
        for (XmlNode citation : refs.elements()) {
            XmlNode doc = citation.find("patcit/document-id");
            if (doc == null) {
                continue; // non-patent literature
            }
            String country = text(doc, "country");
            String number = text(doc, "doc-number");
            if (number == null) {
                continue;
            }
            String kind = text(doc, "kind");
            String cited = country == null || "US".equalsIgnoreCase(country)
                    ? MapperSupport.patentNumber(number, kind)
                    : country + number + (kind == null ? "" : kind);
            String category = text(citation, "category");
            boolean examiner = category != null && category.toLowerCase(Locale.ROOT).contains("examiner");
            result.add(new Citation(cited, examiner ? "EXAMINER" : "APPLICANT"));
        }
        return result;
    }

    private static List<Party> inventors(XmlNode bib) {
        List<Party> result = new ArrayList<>();
        XmlNode parties = bib.child("us-parties");
        if (parties == null) {
            parties = bib.child("parties");
        }
        if (parties == null) {
            return result;
        }
        XmlNode inventors = parties.child("inventors");
        if (inventors != null) {
            for (XmlNode inventor : inventors.children("inventor")) {
                addParty(result, inventor.child("addressbook"));
            }
        }
        if (result.isEmpty()) {
            // older ICE versions: applicants flagged as applicant-inventor
            for (XmlNode applicant : parties.descendants("us-applicant")) {
                String appType = applicant.attr("app-type");
                if (appType != null && appType.contains("inventor")) {
                    addParty(result, applicant.child("addressbook"));
                }
            }
        }
        return result;
    }

    private static List<Party> assignees(XmlNode bib) {
        List<Party> result = new ArrayList<>();
        XmlNode assignees = bib.child("assignees");
        if (assignees != null) {
            for (XmlNode assignee : assignees.children("assignee")) {
                XmlNode book = assignee.child("addressbook");
                addParty(result, book == null ? assignee : book);
            }
        }
        if (result.isEmpty()) {
            for (XmlNode applicant : bib.descendants("us-applicant")) {
                if ("assignee".equalsIgnoreCase(applicant.attr("applicant-authority-category"))) {
                    addParty(result, applicant.child("addressbook"));
                }
            }
        }
        return result;
    }

    private static void addParty(List<Party> out, XmlNode book) {
        if (book == null) {
            return;
        }
        String name = text(book, "orgname");
        if (name == null) {
            name = joinName(text(book, "first-name"), text(book, "last-name"));
        }
        if (name == null) {
            return;
        }
        XmlNode address = book.child("address");
        out.add(new Party(name, text(address, "city"), text(address, "state"), text(address, "country")));
    }
}
