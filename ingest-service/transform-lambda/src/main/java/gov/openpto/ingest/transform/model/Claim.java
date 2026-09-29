package gov.openpto.ingest.transform.model;

/** One claim; {@code dependsOn} is the claim number referenced by a dependent claim, {@code null} if independent. */
public record Claim(int number, String text, boolean independent, Integer dependsOn) {
}