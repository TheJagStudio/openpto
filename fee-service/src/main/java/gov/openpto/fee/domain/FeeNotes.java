package gov.openpto.fee.domain;

/** Standard notes attached to quotes. */
public final class FeeNotes {

    public static final String ILLUSTRATIVE =
            "Amounts are illustrative, modeled on the USPTO fee schedule; they are not official fee advice.";
    public static final String ENTITY_DISCOUNT =
            "Small-entity amounts are 40% and micro-entity amounts 20% of the large-entity fee (stored per schedule).";
    public static final String TRADEMARK_PER_CLASS =
            "Trademark fees are charged per class of goods/services; there are no entity-size discounts.";

    private FeeNotes() {
    }
}
